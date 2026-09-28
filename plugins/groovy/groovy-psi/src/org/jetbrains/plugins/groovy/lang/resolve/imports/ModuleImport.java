// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.imports;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.isNonAnnotationResolve;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessClasses;

/**
 * @author Bas Leijdekkers
 */
public final class ModuleImport implements GroovyStarImport {

  private final String myModuleName;

  public ModuleImport(String moduleName) {
    myModuleName = moduleName;
  }

  @Override
  public @NotNull String getFqn() {
    return myModuleName;
  }

  public @NotNull String getModuleName() {
    return myModuleName;
  }

  @Override
  public @Nullable PsiJavaModule resolveImport(@NotNull GroovyFileBase file) {
    return JavaPsiFacade.getInstance(file.getProject()).findModule(myModuleName, file.getResolveScope());
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @NotNull PsiElement place,
                                     @NotNull GroovyFileBase file) {
    if (isNonAnnotationResolve(processor)) return true;
    if (!shouldProcessClasses(processor)) return true;
    PsiJavaModule module = resolveImport(file);
    if (module == null) return true;
    return module.processDeclarations(processor, state, null, place);
  }

  @Override
  public boolean isUnnecessary(@NotNull GroovyFileImports imports) {
    return false;
  }

  @Override
  public String toString() {
    return "import module " + myModuleName;
  }
}
