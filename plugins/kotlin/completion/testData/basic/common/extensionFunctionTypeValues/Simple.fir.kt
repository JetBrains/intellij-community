package ppp

val fooGlobal: Int.() -> Unit = { }

val String.fooExt: Int.() -> Unit get() = { }

fun String.test(i: Int, foo: Int.(String) -> Char, fooAny: Any.() -> Unit) {
    i.fo<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "(String) for Int", typeText: "Char", attributes: "bold", icon: "Parameter"}
// EXIST: { lookupString: "fooAny", itemText: "fooAny", tailText: "() for Any", typeText: "Unit", attributes: "", icon: "Parameter"}
// EXIST: { lookupString: "fooGlobal", itemText: "fooGlobal", tailText: "() for Int in ppp", typeText: "Unit", attributes: "bold", "icon":"org/jetbrains/kotlin/idea/icons/field_value.svg"}
// ABSENT: fooExt
// IGNORE_K2