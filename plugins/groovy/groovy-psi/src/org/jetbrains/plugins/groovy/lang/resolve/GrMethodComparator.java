// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument;

import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class GrMethodComparator {

  public static final ExtensionPointName<GrMethodComparator> EP_NAME = ExtensionPointName.create("org.intellij.groovy.methodComparator");

  public interface Context {

    default @Nullable List<Argument> getArguments() {
      PsiType[] types = getArgumentTypes();
      return types == null ? null : ContainerUtil.map(types, JustTypeArgument::new);
    }

    PsiType[] getArgumentTypes();

    @NotNull
    PsiElement getPlace();

    boolean isConstructor();
  }

  public abstract Boolean dominated(@NotNull GroovyMethodResult result1,
                                    @NotNull GroovyMethodResult result2,
                                    @NotNull Context context);

  /**
   * @return method1 has more general parameter types than method2
   */
  public static @NotNull Boolean checkDominated(@NotNull GroovyMethodResult result1,
                                       @NotNull GroovyMethodResult result2,
                                       @NotNull Context context) {
    for (GrMethodComparator comparator : EP_NAME.getExtensions()) {
      Boolean result = comparator.dominated(result1, result2, context);
      if (result != null) {
        return result;
      }
    }
    return true;
  }

  /**
   * @return <ul>
   * <li>1 if second is more preferable</li>
   * <li>0 if methods are equal</li>
   * <li>-1 if first is more preferable</li>
   * </ul>
   */
  public static int compareMethods(@NotNull GroovyMethodResult result1, @NotNull GroovyMethodResult result2, @NotNull Context context) {
    final PsiMethod method1 = result1.getElement();
    final PsiMethod method2 = result2.getElement();

    if (!method1.getName().equals(method2.getName())) return 0;

    boolean firstIsPreferable = checkDominated(result2, result1, context);
    boolean secondIsPreferable = checkDominated(result1, result2, context);

    if (firstIsPreferable == secondIsPreferable) {
      if (method1 instanceof GrGdkMethod && !(method2 instanceof GrGdkMethod)) {
        return 1;
      }
      if (!(method1 instanceof GrGdkMethod) && method2 instanceof GrGdkMethod) {
        return -1;
      }
      if (secondIsPreferable) {
        return 1;
      }
      else {
        return 0;
      }
    }
    else if (firstIsPreferable) {
      return -1;
    }
    else {
      return 1;
    }
  }
}
