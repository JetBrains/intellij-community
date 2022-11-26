// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import dependency.xxx

class C {
    fun xxx(){}
    fun xxy(){}
    fun xxz(p: Int){}

    fun f() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "Unit", icon: "Method"}
// EXIST: { lookupString: "xxy", itemText: "xxy", tailText: "()", typeText: "Unit", icon: "Method"}
// EXIST: { lookupString: "xxz", itemText: "xxz", tailText: "(p: Int)", typeText: "Unit", icon: "Method"}
// EXIST: { lookupString: "xxz", itemText: "xxz", tailText: "() (dependency)", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
