package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

/**
 * @author peter
 */
public abstract class GrTypeConverter {
  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.typeConverter");

  protected static boolean isMethodCallConversion(GroovyPsiElement context) {
    return context.getParent() instanceof GrMethodCall;
  }

  @Nullable
  public abstract Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context);

}
