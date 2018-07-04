// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NewGroovyClassNamingConventionInspection extends AbstractNamingConventionInspection<PsiClass> {
  public NewGroovyClassNamingConventionInspection() {
    super(wrapClassExtensions(), "Groovy" + ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
  }

  private static List<NamingConvention<PsiClass>> wrapClassExtensions() {
    return Arrays.stream(NewClassNamingConventionInspection.EP_NAME.getExtensions())
      .map(ex -> new NamingConvention<PsiClass>() {
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
          return "Groovy" + (shortName.startsWith("Enum") ? "EnumerationNamingConvention" : shortName);
        }

        @Override
        public NamingConventionBean createDefaultBean() {
          return ex.createDefaultBean();
        }
      })
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!(holder.getFile() instanceof PsiClassOwner)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
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
