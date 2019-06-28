// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.openapi.util.text.StringUtil;
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
  private static final NodeFilter ourFilter = LexicalNodesFilter.getInstance();

  static {
    for (StructuralSearchProfile profile : StructuralSearchProfile.EP_NAME.getExtensionList()) {
      ourReservedWords.addAll(profile.getReservedWords());
    }
  }

  private CompileContext context;
  private final List<PsiElement> myLexicalNodes = new SmartList<>();
  private int myCodeBlockLevel;

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
    if (elements.length == 0) {
      throw new MalformedPatternException();
    }
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

    word = content.substring(start);

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
    }

    if (!handlers.isEmpty()) {
      return hasLiteralContent
             ? new LiteralWithSubstitutionHandler(buf.toString(), handlers, context.getOptions().isCaseSensitiveMatch())
             : handler;
    }

    return null;
  }

  @Contract("null,_ -> false")
  public static boolean isSuitablePredicate(RegExpPredicate predicate, SubstitutionHandler handler) {
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
    for (String word : StringUtil.getWordsInStringLongestFirst(name)) {
      addFilesToSearchForGivenWord(word, true, kind, getContext());
    }
  }

  public enum OccurenceKind {
    LITERAL, COMMENT, CODE, TEXT
  }
}
