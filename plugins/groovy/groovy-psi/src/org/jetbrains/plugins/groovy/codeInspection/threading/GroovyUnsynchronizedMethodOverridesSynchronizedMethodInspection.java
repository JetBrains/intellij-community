// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyUnsynchronizedMethodOverridesSynchronizedMethodInspection extends BaseInspection {

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.unsynchronized.method.ref.overrides.synchronized.method");

  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        if (superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          registerMethodError(method);
          return;
        }
      }
    }
  }
}
