// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
open class A {
    fun String.fooInherited() {}
}

object AObject : A() {
    fun String.fooExplicit() {}
}

fun usage() {
    "".foo<caret>
}

// EXIST: { "lookupString": "fooExplicit", "itemText": "fooExplicit", icon: "Function"}
// EXIST: { "lookupString": "fooInherited", "itemText": "fooInherited", icon: "Function"}
