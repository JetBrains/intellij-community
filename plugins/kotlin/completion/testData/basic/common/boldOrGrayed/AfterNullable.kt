// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun String?.forNullableString(){}
fun Any?.forNullableAny(){}
fun String.forString(){}
fun Any.forAny(){}

fun foo(s: String?) {
    s.<caret>
}

// EXIST: { lookupString: "forNullableString", attributes: "bold", icon: "Function"}
// EXIST: { lookupString: "forNullableAny", attributes: "", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "forAny", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "Function"}
