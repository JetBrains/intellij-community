// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ppp

class C {
    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Any in dependency1", typeText: "Int", icon: "Function"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in dependency2", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
