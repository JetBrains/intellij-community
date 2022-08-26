// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ppp

import dependency.*

class C {
    private fun xxx() {}

    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "Unit", icon: "Method"}
// NOTHING_ELSE
