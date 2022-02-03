// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

internal data class ClosureFrame constructor(val startInstructionState: TypeDfaState, val startInstructionNumber: Int)