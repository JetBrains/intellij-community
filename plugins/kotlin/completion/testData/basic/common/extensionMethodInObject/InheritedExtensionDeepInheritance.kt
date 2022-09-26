// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class T {
    companion object
}

open class A {
    fun T.Companion.foo() {}
}

open class B: A()
open class C: B()
open class D: C()

object DObject: D()

fun usage() {
    T.<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", icon: "Function"}
