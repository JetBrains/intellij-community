// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.naming.AbstractNamingConventionInspection;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class NewGroovyClassNamingConventionInspection extends AbstractNamingConventionInspection<PsiClass> {
  @NonNls private static final String GROOVY = "Groovy";

  public NewGroovyClassNamingConventionInspection() {
    super(NewClassNamingConventionInspection.EP_NAME.getExtensionList(), GROOVY + ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
    registerConventionsListener(NewClassNamingConventionInspection.EP_NAME);
  }

  private static NamingConvention<PsiClass> wrapClassExtension(NamingConvention<PsiClass> ex) {
    return new NamingConvention<>() {
      @Override
      public boolean isApplicable(PsiClass member) {
        return ex.isApplicable(member);
      }

      @Override
      public String getElementDescription() {
        return ex.getElementDescription();
      }

      @Override
      public String getShortName() {
        String shortName = ex.getShortName();
        if (shortName.startsWith("JUnit")) return shortName;
        return GROOVY + (shortName.startsWith("Enum") ? "EnumerationNamingConvention" : shortName);
      }

      @Override
      public NamingConventionBean createDefaultBean() {
        return ex.createDefaultBean();
      }
    };
  }

  @Override
  protected void registerConvention(NamingConvention<PsiClass> convention) {
    super.registerConvention(wrapClassExtension(convention));
  }

  @Override
  protected void unregisterConvention(@NotNull NamingConvention<PsiClass> extension) {
    super.unregisterConvention(wrapClassExtension(extension));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!(holder.getFile() instanceof PsiClassOwner)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof GrTypeDefinition) {
          PsiClass aClass = (PsiClass)element;
          final String name = aClass.getName();
          if (name == null) return;
          checkName(aClass, name, holder);
        }
      }
    };
  }

  @Override
  protected LocalQuickFix createRenameFix() {
    return GroovyQuickFixFactory.getInstance().createRenameFix();
  }
}
