// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;

public interface GrDelegatesToProvider {

  ExtensionPointName<GrDelegatesToProvider> EP_NAME = ExtensionPointName.create("org.intellij.groovy.delegatesToProvider");

  @Nullable
  DelegatesToInfo getDelegatesToInfo(@NotNull GrFunctionalExpression expression);
}
