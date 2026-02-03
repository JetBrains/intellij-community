// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiPolyVariantReference;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface GroovyDependentReference extends GroovyReference {

  @Nullable
  Collection<? extends PsiPolyVariantReference> collectDependencies();
}
