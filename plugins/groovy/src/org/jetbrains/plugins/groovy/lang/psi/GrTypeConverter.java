package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class GrTypeConverter {
  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.typeConverter");

  @Nullable
  public abstract Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, PsiManager manager, GlobalSearchScope scope);

}