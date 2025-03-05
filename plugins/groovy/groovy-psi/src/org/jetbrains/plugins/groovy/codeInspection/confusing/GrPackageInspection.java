// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ExpectedPackageNameProviderKt;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Max Medvedev
 */
public final class GrPackageInspection extends BaseInspection {
  public boolean myCheckScripts = true;

  @Override
  protected @Nullable String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.package.name.mismatch");
  }

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      checkbox("myCheckScripts", GroovyBundle.message("gr.package.inspection.check.scripts")));
  }

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitFile(@NotNull GroovyFileBase file) {
        if (!(file instanceof GroovyFile)) return;

        if (!myCheckScripts && file.isScript()) return;

        String expectedPackage = ExpectedPackageNameProviderKt.inferExpectedPackageName((GroovyFile)file);
        String actual = file.getPackageName();
        if (!expectedPackage.equals(actual)) {

          PsiElement toHighlight = getElementToHighlight((GroovyFile)file);
          if (toHighlight == null) return;

          registerError(toHighlight,
                        GroovyBundle.message("inspection.message.package.name.mismatch.actual.0.expected.1", actual, expectedPackage),
                        new LocalQuickFix[]{new ChangePackageQuickFix(expectedPackage),
                          GroovyQuickFixFactory.getInstance().createGrMoveToDirFix(actual)},
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private static @Nullable PsiElement getElementToHighlight(GroovyFile file) {
    GrPackageDefinition packageDefinition = file.getPackageDefinition();
    if (packageDefinition != null) return packageDefinition;

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      if (!(aClass instanceof SyntheticElement) && aClass instanceof GrTypeDefinition) {
        return ((GrTypeDefinition)aClass).getNameIdentifierGroovy();
      }
    }

    GrTopStatement[] statements = file.getTopStatements();
    if (statements.length > 0) {
      GrTopStatement first = statements[0];
      if (first instanceof GrNamedElement) return ((GrNamedElement)first).getNameIdentifierGroovy();

      return first;
    }

    return null;
  }

  public static class ChangePackageQuickFix extends PsiUpdateModCommandQuickFix {
    private final String myNewPackageName;

    public ChangePackageQuickFix(String newPackageName) {
      myNewPackageName = newPackageName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return GroovyBundle.message("fix.package.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiFile file = element.getContainingFile();
      ((GroovyFile)file).setPackageName(myNewPackageName);
    }
  }
}
