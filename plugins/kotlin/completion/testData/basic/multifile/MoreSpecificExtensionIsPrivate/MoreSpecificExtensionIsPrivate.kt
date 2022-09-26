// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ppp

import dependency.*

class C

fun foo(c: C) {
    c.xx<caret>
}

// we should not include inaccessible extension even on the second completion because the call won't resolve into it anyway
// INVOCATION_COUNT: 2
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Any in dependency", typeText: "Int", icon: "Function"}
// NOTHING_ELSE
