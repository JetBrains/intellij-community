// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main() {
    useA(G("g"))
}

fun useA(a: A) {
    <caret>println()
}

open class A {
    val aField: String = "aaa"
    fun aFoo(): Int { return 111 }
}

interface D {
    val d: String
}

open class E(val e: String) : D, A() {
    override val d: String
        get() = "d from E"
}

class G(val g: String) : E(g) {
    override val d: String
        get() = "d from G"
}

private fun D.dExtension() {}

fun E.eExtension(str: String) {}

fun G.gExtension(a: Int): String { return "g $a" }

// INVOCATION_COUNT: 1
// EXIST: { itemText: "aField", attributes: "bold" }
// EXIST: { itemText: "aFoo", tailText: "()", attributes: "bold" }
// EXIST: { itemText: "d", attributes: "" }
// EXIST: { itemText: "e", attributes: "" }
// EXIST: { itemText: "g", attributes: "bold" }
// EXIST: { itemText: "dExtension", tailText: "() for D in <root>", attributes: "" }
// EXIST: { itemText: "eExtension", tailText: "(str: String) for E in <root>", attributes: "" }
// EXIST: { itemText: "gExtension", tailText: "(a: Int) for G in <root>", attributes: "bold" }


// RUNTIME_TYPE: G
// IGNORE_K2