// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_COMPARISON
// REGISTRY: kotlin.k2.smart.completion.enabled true
import java.lang.annotation.ElementType

fun foo() {
  var e: ElementType? = <caret>
}

// EXIST: { lookupString:"TYPE", itemText:"ElementType.TYPE", typeText:"ElementType" }
// EXIST: { lookupString:"FIELD", itemText:"ElementType.FIELD", typeText:"ElementType" }
