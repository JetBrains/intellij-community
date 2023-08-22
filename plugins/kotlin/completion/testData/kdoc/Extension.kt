// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// FIR_COMPARISON
// FIR_IDENTICAL
package p

/**
 * [a<caret>]
 */
fun Int.az(ap: String) {

}

fun Int.aaa() {

}

class aZ

// EXIST: az
// EXIST: aZ
// EXIST: ap
// EXIST: aaa
