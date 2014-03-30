/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.RegExpFile;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.intellij.lang.regexp.psi.RegExpRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that is used to validate the "Value-Pattern" textfield: The regex entered there should contain exactly
 * one capturing group that determines the text-range the configured language will be injected into.
 */
public class ValueRegExpAnnotator implements Annotator {
  public static final Key<Boolean> KEY = Key.create("IS_VALUE_REGEXP");

  static {
    // inject annotator one the class is referenced
    LanguageAnnotators.INSTANCE.addExplicitExtension(RegExpLanguage.INSTANCE, new ValueRegExpAnnotator());
  }

  public ValueRegExpAnnotator() {
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (psiElement instanceof RegExpFile && psiElement.getCopyableUserData(KEY) == Boolean.TRUE) {
      final PsiElement pattern = psiElement.getFirstChild();
      if (!(pattern instanceof RegExpPattern)) {
        return;
      }

      final RegExpBranch[] branches = ((RegExpPattern)pattern).getBranches();
      if (branches.length == 1 && branches[0].getAtoms().length == 0) {
        return;
      }

      for (RegExpBranch branch : branches) {
        final int[] count = new int[1];
        branch.accept(new RegExpRecursiveElementVisitor() {
          @Override
          public void visitRegExpGroup(RegExpGroup group) {
            if (group.isCapturing()) {
              count[0]++;
            }
            super.visitRegExpGroup(group);
          }
        });

        if (count[0] != 1) {
          holder.createWarningAnnotation(branch, "The pattern should contain exactly one capturing group");
        }
      }
    }
  }
}
