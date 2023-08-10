// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_COMPARISON
// FIR_IDENTICAL
class A {
    val xxx: Int = this.xx<caret>
}

// ABSENT: xxx