// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchUtil;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.CompositeNodeFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author maxim
 */
public class GlobalCompilingVisitor {
  @NonNls private static final String SUBSTITUTION_PATTERN_STR = "\\b(__\\$_\\w+)\\b";
  private static final Pattern ourSubstitutionPattern = Pattern.compile(SUBSTITUTION_PATTERN_STR);
  private static final NodeFilter ourFilter = LexicalNodesFilter.getInstance();

  private CompileContext context;
  private final List<PsiElement> myLexicalNodes = new SmartList<>();
  private int myCodeBlockLevel;

  @NotNull
  public static NodeFilter getFilter() {
    return ourFilter;
  }

  public void setHandler(@NotNull PsiElement element, @NotNull MatchingHandler handler) {
    MatchingHandler realHandler = context.getPattern().getHandlerSimple(element);

    if (realHandler instanceof SubstitutionHandler) {
      ((SubstitutionHandler)realHandler).setMatchHandler(handler);
    }
    else {
      // @todo care about composite handler in this case of simple handler!
      context.getPattern().setHandler(element, handler);
    }
  }

  public final void handle(@NotNull PsiElement element) {
    if ((!ourFilter.accepts(element) ||
         StructuralSearchUtil.isIdentifier(element)) &&
        context.getPattern().isRealTypedVar(element) &&
        context.getPattern().getHandlerSimple(element) == null
      ) {
      final CompiledPattern pattern = context.getPattern();
      String name = pattern.getTypedVarString(element);
      // name is the same for named element (clazz,methods, etc) and token (name of ... itself)
      // @todo need fix this

      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(name);
      if (handler == null) return;
      pattern.setHandler(element, handler);

      if (context.getOptions().getVariableConstraint(handler.getName()).isPartOfSearchResults()) {
        handler.setTarget(true);
        final PsiElement targetNode = pattern.getTargetNode();
        if (targetNode == null || targetNode == element.getParent()) {
          pattern.setTargetNode(element);
        }
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

  public static void setFilter(@NotNull MatchingHandler handler, @NotNull NodeFilter filter) {
    if (handler.getFilter() != null && handler.getFilter().getClass() != filter.getClass()) {
      // for constructor we will have the same handler for class and method and tokens itself
      handler.setFilter(new CompositeNodeFilter(filter, handler.getFilter()));
    }
    else {
      handler.setFilter(filter);
    }
  }

  public void setFilterSimple(@NotNull PsiElement element, @NotNull NodeFilter filter) {
    context.getPattern().getHandler(element).setFilter(filter);
  }

  @NotNull
  public List<PsiElement> getLexicalNodes() {
    return myLexicalNodes;
  }

  public void addLexicalNode(@NotNull PsiElement node) {
    myLexicalNodes.add(node);
  }

  void compile(PsiElement @NotNull [] elements, @NotNull CompileContext context) {
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

  public boolean hasFragments(@NotNull String pattern) {
    return ourSubstitutionPattern.matcher(pattern).find();
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(@NotNull String pattern, @NotNull OccurenceKind kind) {
    return processPatternStringWithFragments(pattern, kind, ourSubstitutionPattern);
  }

  @Nullable
  public MatchingHandler processPatternStringWithFragments(@NotNull String pattern, @NotNull OccurenceKind kind,
                                                           @NotNull Pattern substitutionPattern) {
    String content;

    if (kind == OccurenceKind.LITERAL) {
      content = pattern.substring(1, pattern.length() - 1);
    }
    else if (kind == OccurenceKind.COMMENT || kind == OccurenceKind.TEXT) {
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
        hasLiteralContent = true;
        buf.append(MatchUtil.makeExtremeSpacesOptional(MatchUtil.shieldRegExpMetaChars(word)));

        processTokenizedName(word, kind);
      }

      handler = (SubstitutionHandler)getContext().getPattern().getHandler(matcher.group(1));
      if (handler == null) throw new MalformedPatternException();

      handlers.add(handler);
      RegExpPredicate predicate = handler.findPredicate(RegExpPredicate.class);

      if (predicate == null || !predicate.isWholeWords()) {
        buf.append("(.*?)");
      }
      else {
        buf.append(".*?\\b(").append(predicate.getRegExp()).append(")\\b.*?");
      }

      if (isSuitablePredicate(predicate, handler)) {
        processTokenizedName(predicate.getRegExp(), kind);
      }

      start = matcher.end();
    }

    word = content.substring(start);

    if (!word.isEmpty()) {
      hasLiteralContent = true;
      buf.append(MatchUtil.makeExtremeSpacesOptional(MatchUtil.shieldRegExpMetaChars(word)));

      processTokenizedName(word, kind);
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
  public static boolean isSuitablePredicate(RegExpPredicate predicate, @NotNull SubstitutionHandler handler) {
    return predicate != null && handler.getMinOccurs() != 0 && predicate.couldBeOptimized();
  }

  public static void addFilesToSearchForGivenWord(@NotNull String word,
                                                  boolean endTransaction,
                                                  @NotNull GlobalCompilingVisitor.OccurenceKind kind,
                                                  @NotNull CompileContext compileContext) {
    if (!compileContext.getSearchHelper().doOptimizing()) {
      return;
    }
    final StructuralSearchProfile profile =
      StructuralSearchUtil.getProfileByFileType(compileContext.getOptions().getFileType());
    assert profile != null;
    if (profile.getReservedWords().contains(word)) return; // skip our special annotations !!!

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

  public void processTokenizedName(@NotNull String name, @NotNull GlobalCompilingVisitor.OccurenceKind kind) {
    if (kind == OccurenceKind.LITERAL) name = StringUtil.unescapeStringCharacters(name);
    for (String word : StringUtil.getWordsInStringLongestFirst(name)) {
      addFilesToSearchForGivenWord(word, true, kind, getContext());
    }
  }

  public enum OccurenceKind {
    LITERAL, COMMENT, CODE, TEXT
  }
}
