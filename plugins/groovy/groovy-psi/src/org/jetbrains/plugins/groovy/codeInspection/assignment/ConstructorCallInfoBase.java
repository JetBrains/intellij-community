// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;

public abstract class ConstructorCallInfoBase<T extends GrConstructorCall> extends CallInfoBase<T> implements ConstructorCallInfo<T> {
  public ConstructorCallInfoBase(T call) {
    super(call);
  }
}
