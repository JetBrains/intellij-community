// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class T

object O {
    fun A.fooForA() {}
    fun A.B.fooForB() {}
    fun A.B.C.fooForC() {}
    fun T.fooForT() {}
}

class A {
    inner class B {
        fun T.usage() {
            foo<caret>
        }

        inner class C {}
    }
}

// EXIST: { lookupString: "fooForA", itemText: "fooForA", icon: "Function"}
// EXIST: { lookupString: "fooForB", itemText: "fooForB", icon: "Function"}
// EXIST: { lookupString: "fooForT", itemText: "fooForT", icon: "Function"}
// ABSENT: fooForC