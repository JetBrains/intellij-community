// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
open class A {
    fun <T> T.fooExtension() {}
    val <T> T.fooProperty get() = 10
}

object AOBject : A()

class B

fun usage(arg: B) {
    arg.foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "Function"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}