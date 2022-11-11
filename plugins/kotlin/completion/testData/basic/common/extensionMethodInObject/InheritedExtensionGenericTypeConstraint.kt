// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
open class A {
    fun <T : CharSequence> T.fooExtCharSequence() {}
    fun <T : Number> T.fooExtNumber() {}
    fun <T> T.fooExtAny() {}
}

object AOBject : A()

fun usage() {
    10.fooE<caret>
}

// EXIST: { lookupString: "fooExtNumber", itemText: "fooExtNumber", icon: "Function"}
// EXIST: { lookupString: "fooExtAny", itemText: "fooExtAny", icon: "Function"}
// ABSENT: { lookupString: "fooExtCharSequence", itemText: "fooExtCharSequence" }
