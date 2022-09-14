// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_IDENTICAL
// FIR_COMPARISON
interface T1 {
    fun inT1(){}
}

interface T2 {
    fun inT2(){}
}

fun T1.forT1(){}
fun T2.forT2(){}
fun T1?.forNullableT1(){}
fun T2?.forNullableT2(){}
fun Any.forAny(){}
fun Any?.forNullableAny(){}

fun foo(o: T1?) {
    if (o is T2) {
        o.<caret>
    }
}

// EXIST: { lookupString: "inT1", attributes: "bold", icon: "Method"}
// EXIST: { lookupString: "inT2", attributes: "bold", icon: "Method"}
// EXIST: { lookupString: "hashCode", attributes: "", icon: "Method"}
// EXIST: { lookupString: "forT1", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forT2", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forNullableT1", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forNullableT2", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "Function"}
