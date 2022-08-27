// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
interface T
interface B: T
interface C: T

object A {
    fun Any.fooForAny() {}

    fun T.fooForT() {}
    fun B.fooForB() {}
    fun C.fooForC() {}

    fun <TT> TT.fooForAnyGeneric() {}
    fun <TT: T> TT.fooForTGeneric() {}
    fun <TT: B> TT.fooForBGeneric() {}
    fun <TT: C> TT.fooForCGeneric() {}

    fun fooNoReceiver() {}
}

fun B.usage() {
    foo<caret>
}

// EXIST: { lookupString: "fooForAny", itemText: "fooForAny", icon: "Function"}

// EXIST: { lookupString: "fooForT", itemText: "fooForT", icon: "Function"}
// EXIST: { lookupString: "fooForB", itemText: "fooForB", icon: "Function"}

// EXIST: { lookupString: "fooForTGeneric", itemText: "fooForTGeneric", icon: "Function"}
// EXIST: { lookupString: "fooForBGeneric", itemText: "fooForBGeneric", icon: "Function"}

// ABSENT: fooNoReceiver

// ABSENT: fooForC
// ABSENT: fooForCGeneric
