// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

/**
 * As {@code yield} and {@code return} both represent exit points for a flow, it makes sense to relate them with some interface
 */
public interface MaybeInterruptInstruction {
}
