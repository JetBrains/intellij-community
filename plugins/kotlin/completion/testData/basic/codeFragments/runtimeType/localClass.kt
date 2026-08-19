// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
interface MyI

fun MyI.myIfaceExt() {}
fun String.myStringExt() {}
operator fun String.invoke() {}
operator fun String.set(index: Int, value: Char) {}

fun main() {
    class Local : MyI {
        val foo = 100
        fun bar() = 222
    }

    val local: Any = Local()
    <caret>val a = 1
}

// Extensions of the local class supertypes (myIfaceExt) are not suggested, see KTIJ-35532.
// INVOCATION_COUNT: 1
// EXIST: foo, bar
// ABSENT: myStringExt
// ABSENT: { itemText: "()" }
// ABSENT: { itemText: "[]" }

// RUNTIME_TYPE: Local
