// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.jarFinder;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

public final class GroovyFindJarQuickFixProvider extends UnresolvedReferenceQuickFixProvider<GrReferenceElement<?>> {
  @Override
  public void registerFixes(@NotNull GrReferenceElement<?> ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new GroovyFindJarFix(ref));
  }

  @Override
  public @NotNull Class<GrReferenceElement<?>> getReferenceClass() {
    //noinspection unchecked
    return (Class<GrReferenceElement<?>>)(Class<?>)GrReferenceElement.class;
  }
}
