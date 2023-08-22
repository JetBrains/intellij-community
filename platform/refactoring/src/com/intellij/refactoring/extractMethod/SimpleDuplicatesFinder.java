// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User : ktisha
 */
public class SimpleDuplicatesFinder {
  private static final Key<PsiElement> PARAMETER = Key.create("PARAMETER");

  protected PsiElement myReplacement;
  private final ArrayList<PsiElement> myPattern;
  private final Set<String> myParameters;
  private final Collection<String> myOutputVariables;
  protected final Map<String, String> myOriginalToDuplicateLocalVariable;

  public SimpleDuplicatesFinder(@NotNull final PsiElement statement1,
                                @NotNull final PsiElement statement2,
                                Collection<String> variables,
                                AbstractVariableData[] variableData) {
    myOriginalToDuplicateLocalVariable = new HashMap<>();
    myOutputVariables = variables;
    myParameters = new HashSet<>();
    for (AbstractVariableData data : variableData) {
      myParameters.add(data.getOriginalName());
    }
    myPattern = new ArrayList<>();
    PsiElement sibling = statement1;

    do {
      myPattern.add(sibling);
      if (sibling == statement2) break;
      sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling);
    } while (sibling != null);
  }

  public List<SimpleMatch> findDuplicates(final @Nullable List<? extends PsiElement> scope,
                                          @NotNull final PsiElement generatedMethod) {
    final List<SimpleMatch> result = new ArrayList<>();
    annotatePattern();
    if (scope != null) {
      for (PsiElement element : scope) {
        findPatternOccurrences(result, element, generatedMethod);
      }
    }
    deannotatePattern();
    return result;
  }

  protected void deannotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          if (element.getUserData(PARAMETER) != null) {
            element.putUserData(PARAMETER, null);
          }
        }
      });
    }
  }

  protected void annotatePattern() {
    for (final PsiElement patternComponent : myPattern) {
      patternComponent.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          if (myParameters.contains(element.getText())) {
            element.putUserData(PARAMETER, element);
          }

        }
      });
    }
  }

  private void findPatternOccurrences(@NotNull final List<? super SimpleMatch> array, @NotNull final PsiElement scope,
                                      @NotNull final PsiElement generatedMethod) {
    if (scope == generatedMethod) return;
    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      final SimpleMatch match = isDuplicateFragment(child);
      if (match != null) {
        array.add(match);
        continue;
      }
      findPatternOccurrences(array, child, generatedMethod);
    }
  }

  @Nullable
  protected SimpleMatch isDuplicateFragment(@NotNull final PsiElement candidate) {
    if (!canReplace(myReplacement, candidate)) return null;
    for (PsiElement pattern : myPattern) {
      if (PsiTreeUtil.isAncestor(pattern, candidate, false)) return null;
    }
    PsiElement sibling = candidate;
    final ArrayList<PsiElement> candidates = new ArrayList<>();
    for (int i = 0; i != myPattern.size(); ++i) {
      if (sibling == null) return null;

      candidates.add(sibling);
      sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(sibling);
    }
    if (myPattern.size() != candidates.size()) return null;
    if (candidates.size() <= 0) return null;
    final SimpleMatch match = new SimpleMatch(candidates.get(0), candidates.get(candidates.size() - 1));
    myOriginalToDuplicateLocalVariable.clear();
    for (int i = 0; i < myPattern.size(); i++) {
      if (!matchPattern(myPattern.get(i), candidates.get(i), match)) return null;
    }
    return match;
  }

  private boolean matchPattern(@Nullable final PsiElement pattern,
                                      @Nullable final PsiElement candidate,
                                      @NotNull final SimpleMatch match) {
    ProgressManager.checkCanceled();
    if (pattern == null || candidate == null) return pattern == candidate;
    final PsiElement[] children1 = PsiEquivalenceUtil.getFilteredChildren(pattern, null, true);
    final PsiElement[] children2 = PsiEquivalenceUtil.getFilteredChildren(candidate, null, true);
    final PsiElement patternParent = pattern.getParent();
    final PsiElement candidateParent = candidate.getParent();
    if (patternParent == null || candidateParent == null) return false;
    if (pattern.getUserData(PARAMETER) != null && patternParent.getClass() == candidateParent.getClass()) {
      if (myOutputVariables.contains(pattern.getText())) {
        if(match.getChangedOutput() == null) {
          match.changeOutput(candidate.getText());
        }
      }
      return changeParameter(pattern.getText(), candidate.getText(), match);
    }
    if (children1.length != children2.length) return false;

    for (int i = 0; i < children1.length; i++) {
      final PsiElement child1 = children1[i];
      final PsiElement child2 = children2[i];
      if (!matchPattern(child1, child2, match)) return false;
    }

    if (children1.length == 0) {
      if (pattern.getUserData(PARAMETER) != null && patternParent.getClass() == candidateParent.getClass()) {
        if (myOutputVariables.contains(pattern.getText())) {
          match.changeOutput(candidate.getText());
        }
        return changeParameter(pattern.getText(), candidate.getText(), match);
      }
      if (myOutputVariables.contains(pattern.getText())) {
        match.changeOutput(candidate.getText());
        return true;
      }
      if (isVariable(pattern) && isVariable(candidate)) {
        if (!myOriginalToDuplicateLocalVariable.containsKey(pattern.getText())) {
          myOriginalToDuplicateLocalVariable.put(pattern.getText(), candidate.getText());
          return true;
        }
        return myOriginalToDuplicateLocalVariable.get(pattern.getText()).equals(candidate.getText());
      }
      if (!pattern.textMatches(candidate)) {
        return false;
      }
    }

    return true;
  }

  protected boolean isVariable(PsiElement element) {
    return false;
  }

  private static boolean changeParameter(@NotNull String from, @NotNull String to, @NotNull SimpleMatch match) {
    if (match.getChangedParameters().containsKey(from) && !match.getChangedParameters().get(from).equals(to) && !from.equals(to)) {
      return false;
    }
    match.changeParameter(from, to);
    return true;
  }

  protected boolean canReplace(PsiElement replacement, PsiElement element) {
    return !PsiTreeUtil.isAncestor(replacement, element, false);
  }

  public void setReplacement(PsiElement replacement) {
    myReplacement = replacement;
  }

}