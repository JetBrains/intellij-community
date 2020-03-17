// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;

/**
 * @author ven
 */
public interface GrConstructorCall extends GrCall {
  GroovyResolveResult[] multiResolveClass();

  @Nullable
  GroovyConstructorReference getConstructorReference();
}
