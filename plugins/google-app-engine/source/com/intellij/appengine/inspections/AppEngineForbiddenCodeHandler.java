package com.intellij.appengine.inspections;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class AppEngineForbiddenCodeHandler {
  public static final ExtensionPointName<AppEngineForbiddenCodeHandler> EP_NAME = ExtensionPointName.create("com.intellij.appengine.forbiddenCodeHandler");

  public abstract boolean isNativeMethodAllowed(@NotNull PsiMethod method);
}
