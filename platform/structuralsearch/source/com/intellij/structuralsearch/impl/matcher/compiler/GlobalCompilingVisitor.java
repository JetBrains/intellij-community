// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.CompositeFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.structuralsearch.MatchOptions.INSTANCE_MODIFIER_NAME;
import static com.intellij.structuralsearch.MatchOptions.MODIFIER_ANNOTATION_NAME;

/**
 * @author maxim
 */
public class GlobalCompilingVisitor {
  @NonNls private static final String SUBSTITUTION_PATTERN_STR = "\\b(__\\$_\\w+)\\b";
  private static final Pattern ourSubstitutionPattern = Pattern.compile(SUBSTITUTION_PATTERN_STR);
  private static final Set<String> ourReservedWords = new HashSet<>(Arrays.asList(MODIFIER_ANNOTATION_NAME, INSTANCE_MODIFIER_NAME));

  static {
    for (StructuralSearchProfile profile : Extensions.getExtensions(StructuralSearchProfile.EP_NAME)) {
      ourReservedWords.addAll(profile.getReservedWords());
    }
  }

  private static final Pattern ourAlternativePattern = Pattern.compile("^\\((.+)\\)$");
  @NonNls private static final String WORD_SEARCH_PATTERN_STR = ".*?\\b(.+?)\\b.*?";
  static final Pattern ourWordSearchPattern = Pattern.compile(WORD_SEARCH_PATTERN_STR);
  private CompileContext context;
  private final List<PsiElement> myLexicalNodes = new SmartList<>();

  private int myCodeBlockLevel;

  private static final NodeFilter ourFilter = LexicalNodesFilter.getInstance();

  public static NodeFilter getFilter() {
    return ourFilter;
  }

  public void setHandler(PsiElement element, MatchingHandler handler) {
    MatchingHandler realHandler = context.getPattern().getHandlerSimple(element);

    if (realHandler instanceof SubstitutionHandler) {
      ((SubstitutionHandler)realHandler).setMatchHandler(handler);
    }
    else {
      // @todo care about composite handler in this case of simple handler!
      context.getPattern().setHandler(element, handler);
    }
  }

  public final void handle(PsiElement element) {
    if ((!ourFilter.accepts(element) ||
         StructuralSearchUtil.isIdentifier(element)) &&
        context.getPattern().isRealTypedVar(element) &&
        context.getPattern().getHandlerSimple(element) == null
      ) {
      String name = context.getPattern().getTypedVarString(element);
      // name is the same for named element (clazz,methods, etc) and token (name of ... itself)
      // @todo need fix this

      final SubstitutionHandler handler = (SubstitutionHandler)context.getPattern().getHandler(name);
      if (handler == null) return;
      context.getPattern().setHandler(element, handler);

      if (context.getOptions().getVariableConstraint(handler.getName()).isPartOfSearchResults()) {
        handler.setTarget(true);
        context.getPattern().setTargetNode(element);
      }
    }
  }

  public CompileContext getContext() {
    return context;
  }

  public int getCodeBlockLevel() {
    return myCodeBlockLevel;
  }

  public void setCodeBlockLevel(int codeBlockLevel) {
    this.myCodeBlockLevel = codeBlockLevel;
  }

  public static void setFilter(MatchingHandler handler, NodeFilter filter) {
    if (handler.getFilter() != null && handler.getFilter().getClass() != filter.getClass()) {
      // for constructor we will have the same handler for class and method and tokens itself
      handler.setFilter(new CompositeFilter(filter, handler.getFilter()));
    }
    else {
      handler.setFilter(filter);
    }
  }

  public void setFilterSimple(PsiElement element, NodeFilter filter) {
    context.getPattern().getHandler(element).setFilter(filter);
  }

  public List<PsiElement> getLexicalNodes() {
    return myLexicalNodes;
  }

  public void addLexicalNode(PsiElement node) {
    myLexicalNodes.add(node);
  }

  void compile(PsiElement[] elements, CompileContext context) {
    myCodeBlockLevel = 0;
    this.context = context;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(context.getOptions().getFileType());
    assert profile != null;
    profile.compile(elements, this);

    assert context.getPattern().getStrategy() != null;
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(String pattern, OccurenceKind kind) {
    return processPatternStringWithFragments(pattern, kind, ourSubstitutionPattern);
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(String pattern, OccurenceKind kind, Pattern substitutionPattern) {
    String content;

    if (kind == OccurenceKind.LITERAL) {
      content = pattern.substring(1, pattern.length() - 1);
    }
    else if (kind == OccurenceKind.COMMENT) {
      content = pattern;
    }
    else {
      assert false;
      return null;
    }

    @NonNls StringBuilder buf = new StringBuilder(content.length());
    Matcher matcher = substitutionPattern.matcher(content);
    List<SubstitutionHandler> handlers = new SmartList<>();
    int start = 0;
    String word;
    boolean hasLiteralContent = false;

    SubstitutionHandler handler = null;
    while (matcher.find()) {
      word = content.substring(start, matcher.start());
      if (!word.isEmpty()) {
        buf.append(StructuralSearchUtil.shieldRegExpMetaChars(word));
        hasLiteralContent = true;

        processTokenizedName(word, false, kind);
      }

      handler = (SubstitutionHandler)getContext().getPattern().getHandler(matcher.group(1));
      if (handler == null) throw new MalformedPatternException();

      handlers.add(handler);
      RegExpPredicate predicate = handler.findRegExpPredicate();

      if (predicate == null || !predicate.isWholeWords()) {
        buf.append("(.*?)");
      }
      else {
        buf.append(".*?\\b(").append(predicate.getRegExp()).append(")\\b.*?");
      }

      if (isSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(), false, kind);
      }

      start = matcher.end();
    }

    word = content.substring(start, content.length());

    if (!word.isEmpty()) {
      hasLiteralContent = true;
      buf.append(StructuralSearchUtil.shieldRegExpMetaChars(word));

      processTokenizedName(word, false, kind);
    }

    if (hasLiteralContent) {
      if (kind == OccurenceKind.LITERAL) {
        buf.insert(0, "[\"']");
        buf.append("[\"']");
      }
      buf.append("$");
    }

    if (!handlers.isEmpty()) {
      return hasLiteralContent ? new LiteralWithSubstitutionHandler(buf.toString(), handlers) : handler;
    }

    return null;
  }

  @Contract("null,_ -> false")
  static boolean isSuitablePredicate(RegExpPredicate predicate, SubstitutionHandler handler) {
    return predicate != null && handler.getMinOccurs() != 0 && predicate.couldBeOptimized();
  }

  public static void addFilesToSearchForGivenWord(String word,
                                                  boolean endTransaction,
                                                  GlobalCompilingVisitor.OccurenceKind kind,
                                                  CompileContext compileContext) {
    if (!compileContext.getSearchHelper().doOptimizing()) {
      return;
    }
    if (ourReservedWords.contains(word)) return; // skip our special annotations !!!

    if (kind == GlobalCompilingVisitor.OccurenceKind.CODE) {
      compileContext.getSearchHelper().addWordToSearchInCode(word);
    }
    else if (kind == GlobalCompilingVisitor.OccurenceKind.COMMENT) {
      compileContext.getSearchHelper().addWordToSearchInComments(word);
    }
    else if (kind == GlobalCompilingVisitor.OccurenceKind.LITERAL) {
      compileContext.getSearchHelper().addWordToSearchInLiterals(word);
    }
    else if (kind == GlobalCompilingVisitor.OccurenceKind.TEXT) {
      compileContext.getSearchHelper().addWordToSearchInText(word);
    }

    if (endTransaction) {
      compileContext.getSearchHelper().endTransaction();
    }
  }

  public void processTokenizedName(String name, boolean skipComments, GlobalCompilingVisitor.OccurenceKind kind) {
    WordTokenizer tokenizer = new WordTokenizer(name);
    for (Iterator<String> i = tokenizer.iterator(); i.hasNext();) {
      String nextToken = i.next();
      if (skipComments &&
          (nextToken.equals("/*") || nextToken.equals("/**") || nextToken.equals("*/") || nextToken.equals("*") || nextToken.equals("//"))
        ) {
        continue;
      }

      Matcher matcher = ourAlternativePattern.matcher(nextToken);
      if (matcher.matches()) {
        StringTokenizer alternatives = new StringTokenizer(matcher.group(1), "|");
        while (alternatives.hasMoreTokens()) {
          addFilesToSearchForGivenWord(alternatives.nextToken(), !alternatives.hasMoreTokens(), kind, getContext());
        }
      }
      else {
        addFilesToSearchForGivenWord(nextToken, true, kind, getContext());
      }
    }
  }

  public enum OccurenceKind {
    LITERAL, COMMENT, CODE, TEXT
  }

  private static class WordTokenizer {
    private final List<String> myWords = new SmartList<>();

    WordTokenizer(String text) {
      final StringTokenizer tokenizer = new StringTokenizer(text);
      Matcher matcher = null;

      while (tokenizer.hasMoreTokens()) {
        String nextToken = tokenizer.nextToken();
        if (matcher == null) {
          matcher = ourWordSearchPattern.matcher(nextToken);
        }
        else {
          matcher.reset(nextToken);
        }

        nextToken = (matcher.matches()) ? matcher.group(1) : nextToken;
        int lastWordStart = 0;
        int i;
        for (i = 0; i < nextToken.length(); ++i) {
          if (!Character.isJavaIdentifierStart(nextToken.charAt(i))) {
            if (i != lastWordStart) {
              myWords.add(nextToken.substring(lastWordStart, i));
            }
            lastWordStart = i + 1;
          }
        }

        if (i != lastWordStart) {
          myWords.add(nextToken.substring(lastWordStart, i));
        }
      }
    }

    Iterator<String> iterator() {
      return myWords.iterator();
    }
  }
}
