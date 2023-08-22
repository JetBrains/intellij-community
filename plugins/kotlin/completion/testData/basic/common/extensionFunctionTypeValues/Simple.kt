package ppp

val fooGlobal: Int.() -> Unit = { }

val String.fooExt: Int.() -> Unit get() = { }

fun String.test(i: Int, foo: Int.(String) -> Char, fooAny: Any.() -> Unit) {
    i.fo<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "(String)", typeText: "Char", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg"}
// EXIST: { lookupString: "fooAny", itemText: "fooAny", tailText: "()", typeText: "Unit", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg"}
// EXIST: { lookupString: "fooGlobal", itemText: "fooGlobal", tailText: "() (ppp)", typeText: "Unit", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg"}
// ABSENT: fooExt