/*
 * Copyright 2007-2008 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.GrInspectionUIUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseInspection extends LocalInspectionTool {

  public @NotNull Set<String> explicitlyEnabledFileTypes = new HashSet<>();

  @Nullable
  @InspectionMessage
  protected String buildErrorString(Object... args) {
    return null;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  @Override
  public final @Nullable JComponent createOptionsPanel() {
    JComponent actualPanel = createGroovyOptionsPanel();
    return GrInspectionUIUtil.enhanceInspectionToolPanel(this, explicitlyEnabledFileTypes, actualPanel);
  }

  protected @Nullable JComponent createGroovyOptionsPanel() {
    return null;
  }

  @Nullable
  protected LocalQuickFix buildFix(@NotNull PsiElement location) {
    return null;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    BaseInspectionVisitor visitor = buildVisitor();
    visitor.initialize(this, holder, isOnTheFly);
    return new GroovyPsiElementVisitor(visitor) {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (GrInspectionUIUtil.checkInspectionEnabledByFileType(BaseInspection.this, element, explicitlyEnabledFileTypes)) {
          super.visitElement(element);
        }
      }
    };
  }

  @NotNull
  protected abstract BaseInspectionVisitor buildVisitor();

  @Nls(capitalization = Nls.Capitalization.Sentence)
  public static String getProbableBugs() {
    return GroovyBundle.message("inspection.bugs");
  }
}
