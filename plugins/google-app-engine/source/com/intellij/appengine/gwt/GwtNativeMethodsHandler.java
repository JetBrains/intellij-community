package com.intellij.appengine.gwt;

import com.intellij.appengine.inspections.AppEngineForbiddenCodeHandler;
import com.intellij.gwt.jsinject.JsInjector;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class GwtNativeMethodsHandler extends AppEngineForbiddenCodeHandler {
  @Override
  public boolean isNativeMethodAllowed(@NotNull PsiMethod method) {
    return JsInjector.isJsniMethod(method);
  }
}
