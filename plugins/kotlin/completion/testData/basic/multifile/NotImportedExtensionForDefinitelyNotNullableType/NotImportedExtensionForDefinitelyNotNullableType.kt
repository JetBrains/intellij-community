// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package root

import p1.MyInterface

fun <T : MyInterface?> foo(a: T & Any) {
    a.ext<caret>
}

// EXIST: { lookupString: "extFunMyInterface", tailText: "() for MyInterface in p1", typeText: "Unit", icon: "Function", attributes: "", allLookupStrings: "extFunMyInterface", itemText: "extFunMyInterface" }
// ABSENT: extFunUnrelated
