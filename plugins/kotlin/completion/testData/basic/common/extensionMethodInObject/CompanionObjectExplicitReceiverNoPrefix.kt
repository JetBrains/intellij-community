// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class T

class A {
    companion object {
        fun T.fooExtension() {}
        val T.fooProperty get() = 10
    }
}

fun usage(t: T) {
    t.<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "Function"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}