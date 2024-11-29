// FIR_COMPARISON

fun foo1(
    optional1: String = "",
    optional2: Int = 1,
    block: () -> Unit,
) {
}

fun foo2(
    optional1: String = "",
    optional2: Int = 1,
    block: (Int) -> Boolean,
) {
}

fun foo3(
    optional1: String = "",
    optional2: Int = 1,
    block: (String, Char) -> Unit,
) {
}

fun foo4(
    block: () -> Unit = {},
) {
}

fun foo5(
    optional: String = "",
    required: Int,
    block: (String, Char) -> Unit,
) {
}

fun foo6(
    optional1: String = "",
    optional2: Int = 1,
    block: (String, Char) -> Unit,
    optional3: Int = 0,
) {
}

fun foo7(
    optional1: String = "",
    vararg block: () -> Unit,
) {
}

fun bar(param: () -> Unit) {
    foo<caret>
}

// WITH_ORDER

// EXIST: { itemText: "foo1", tailText: " { block: () -> Unit } (<root>)" }
// EXIST: { itemText: "foo1", tailText: "(optional1: String = ..., optional2: Int = ..., block: () -> Unit) (<root>)" }

// EXIST: { itemText: "foo2", tailText: " { block: (Int) -> Boolean } (<root>)" }
// EXIST: { itemText: "foo2", tailText: "(optional1: String = ..., optional2: Int = ..., block: (Int) -> Boolean) (<root>)" }

// EXIST: { itemText: "foo3", tailText: " { block: (String, Char) -> Unit } (<root>)" }
// EXIST: { itemText: "foo3", tailText: "(optional1: String = ..., optional2: Int = ..., block: (String, Char) -> Unit) (<root>)" }

// EXIST: { itemText: "foo4", tailText: " { block: () -> Unit } (<root>)" }
// EXIST: { itemText: "foo4", tailText: "(block: () -> Unit = ...) (<root>)" }
/* TODO EXIST: { itemText: "foo4", tailText: "(param) (<root>)" } */

// EXIST: { itemText: "foo5", tailText: "(optional: String = ..., required: Int, block: (String, Char) -> Unit) (<root>)" }
// EXIST: { itemText: "foo6", tailText: "(optional1: String = ..., optional2: Int = ..., block: (String, Char) -> Unit, optional3: Int = ...) (<root>)" }
// EXIST: { itemText: "foo7", tailText: "(optional1: String = ..., vararg block: () -> Unit) (<root>)" }

// NOTHING_ELSE