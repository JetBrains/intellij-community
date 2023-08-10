// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class T

class A {
    companion object {
        infix fun T.fooExtension(i: Int) {}
    }
}

fun usage(t: T) {
    t foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "Function"}
