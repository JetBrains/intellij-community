// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.naming.AbstractNamingConventionInspection;
import com.intellij.codeInspection.naming.NamingConvention;
import com.intellij.codeInspection.naming.NamingConventionBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class FieldNamingConventionInspection extends AbstractNamingConventionInspection<PsiField> {
  public static final ExtensionPointName<NamingConvention<PsiField>> EP_NAME = ExtensionPointName.create("com.intellij.naming.convention.field");
  public FieldNamingConventionInspection() {
    super(Arrays.asList(EP_NAME.getExtensions()), null);
  }

  @Nullable
  @Override
  protected LocalQuickFix createRenameFix() {
    return new RenameFix();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(PsiField field) {
        String name = field.getName();
        if (name != null) {
          checkName(field, name, holder);
        }
      }
    };
  }

  static class FieldNamingConventionBean extends NamingConventionBean {
    public FieldNamingConventionBean(String regex, int minLength, int maxLength) {
      super(regex, minLength, maxLength, HardcodedMethodConstants.SERIAL_VERSION_UID);
    }
  }
}
