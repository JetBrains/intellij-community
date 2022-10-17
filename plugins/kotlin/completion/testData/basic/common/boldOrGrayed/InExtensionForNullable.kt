// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun String.forString(){}

fun globalFun(){}

fun String?.foo() {
    <caret>
}

// EXIST: { lookupString: "globalFun", attributes: "", icon: "Function"}
// EXIST: { lookupString: "compareTo", attributes: "grayed", icon: "Function"}
// EXIST: { lookupString: "forString", attributes: "grayed", icon: "Function"}
