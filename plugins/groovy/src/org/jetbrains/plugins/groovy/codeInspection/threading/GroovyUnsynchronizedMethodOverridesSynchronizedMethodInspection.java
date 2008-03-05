package org.jetbrains.plugins.groovy.codeInspection.threading;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyUnsynchronizedMethodOverridesSynchronizedMethodInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return THREADING_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unsynchronized method overrides synchronized method";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Unsynchronized method '#ref' overrides a synchronized method #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod method) {
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