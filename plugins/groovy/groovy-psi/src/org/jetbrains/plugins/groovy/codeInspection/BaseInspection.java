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
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.GrInspectionUIUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * For new inspections, please use {@link GroovyLocalInspectionTool}.
 */
public abstract class BaseInspection extends LocalInspectionTool {

  public @NotNull Set<String> explicitlyEnabledFileTypes = new HashSet<>();

  protected @Nullable @InspectionMessage String buildErrorString(Object... args) {
    return null;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  @Override
  public final @NotNull OptPane getOptionsPane() {
    OptPane pane = getGroovyOptionsPane();
    return GrInspectionUIUtil.enhanceInspectionToolPanel(this, pane);
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onPrefix(
      "fileType", GrInspectionUIUtil.getFileTypeController(explicitlyEnabledFileTypes));
  }

  protected @NotNull OptPane getGroovyOptionsPane() {
    return OptPane.EMPTY;
  }

  protected @Nullable LocalQuickFix buildFix(@NotNull PsiElement location) {
    return null;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
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

  protected abstract @NotNull BaseInspectionVisitor buildVisitor();

  public static @Nls(capitalization = Nls.Capitalization.Sentence) String getProbableBugs() {
    return GroovyBundle.message("inspection.bugs");
  }
}
