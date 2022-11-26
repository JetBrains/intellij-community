// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The WordOptimizer is used for extracting words to check in the index. Basically it is just an optimization for faster search,
 * because files without the extracted words don’t need to be scanned. That means you can create Structural Search for a language
 * without a WordOptimizer and it will still be correct, just slower.
 *
 * @author Bas Leijdekkers
 */
public interface WordOptimizer {

  /**
   * @param text  text to check index with
   * @param kind  LITERAL, COMMENT, CODE or TEXT
   * @return true, if psi tree should be processed deeper, false otherwise.
   */
  default boolean handleWord(@Nullable String text, GlobalCompilingVisitor.OccurenceKind kind, CompileContext compileContext) {
    final OptimizingSearchHelper searchHelper = compileContext.getSearchHelper();
    if (!searchHelper.doOptimizing()) {
      return false;
    }
    if (text == null) {
      return true;
    }
    for (String word : StringUtil.getWordsInStringLongestFirst(text)) {
      final CompiledPattern pattern = compileContext.getPattern();
      if (pattern.isTypedVar(word)) {
        final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(word);
        if (handler == null || handler.getMinOccurs() == 0) {
          // don't call super visit so psi tree is not processed deeper
          return false;
        }

        final RegExpPredicate predicate = handler.findPredicate(RegExpPredicate.class);
        if (predicate != null && predicate.couldBeOptimized()) {
          if (handler.isStrictSubtype() || handler.isSubtype()) {
            final List<String> descendants = getDescendantsOf(predicate.getRegExp(), handler.isSubtype(), compileContext.getProject());
            for (String descendant : descendants) {
              searchHelper.addWordToSearchInCode(descendant);
            }
            searchHelper.endTransaction();
          }
          else {
            GlobalCompilingVisitor.addFilesToSearchForGivenWord(predicate.getRegExp(), true, kind, compileContext);
          }
        }
      }
      else {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(word, true, kind, compileContext);
      }
    }
    return true;
  }

  /**
   * Subtype handling for those structural search implementations that support it. If subtype matching is not supported, this
   * method does not need to be overridden.
   * @param className  the name of the class to search for subclasses of
   * @param includeSelf  include the class itself in the search
   */
  @NotNull
  default List<String> getDescendantsOf(@NotNull String className, boolean includeSelf, @NotNull Project project) {
    return Collections.emptyList();
  }
}
