// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.CODE;

/**
 * @author Bas Leijdekkers
 */
public interface WordOptimizer {

  /**
   * @param word  word to check index with
   * @return true, if psi tree should be processed deeper, false otherwise.
   */
  default boolean handleWord(@Nullable String word, CompileContext compileContext) {
    final OptimizingSearchHelper searchHelper = compileContext.getSearchHelper();
    if (!searchHelper.doOptimizing()) {
      return false;
    }
    if (word == null) {
      return true;
    }
    final CompiledPattern pattern = compileContext.getPattern();
    if (pattern.isTypedVar(word)) {
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(word);
      if (handler == null || handler.getMinOccurs() == 0) {
        // don't call super
        return false;
      }

      final RegExpPredicate predicate = handler.findRegExpPredicate();
      if (predicate != null && predicate.couldBeOptimized()) {
        if (handler.isStrictSubtype() || handler.isSubtype()) {
          final List<String> descendants = getDescendantsOf(predicate.getRegExp(), handler.isSubtype(), compileContext.getProject());
          for (String descendant : descendants) {
            searchHelper.addWordToSearchInCode(descendant);
          }
          searchHelper.endTransaction();
        }
        else {
          GlobalCompilingVisitor.addFilesToSearchForGivenWord(predicate.getRegExp(), true, CODE, compileContext);
        }
      }
    }
    else {
      GlobalCompilingVisitor.addFilesToSearchForGivenWord(word, true, CODE, compileContext);
    }
    return true;
  }

  /**
   * Subtype handling for those structural search implementations that support it. If subtype matching is not supported, this
   * method does not need to be overridden.
   * @param className  the name of the class to search for subclasses of
   * @param includeSelf  include the class itself in the search
   * @param context
   */
  default List<String> getDescendantsOf(String className, boolean includeSelf, Project project) {
    return Collections.emptyList();
  }
}
