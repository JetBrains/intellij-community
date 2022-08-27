// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_IDENTICAL
// FIR_COMPARISON
fun String?.forNullableString(){}
fun Any?.forNullableAny(){}
fun String.forString(){}
fun Any.forAny(){}

fun foo(o: Any?) {
    if (o is String) {
        o.<caret>
    }
}

// EXIST: { lookupString: "forNullableString", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "", icon: "Method"}
