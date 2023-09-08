// FIR_COMPARISON
class A {
    val foo: Int.() -> Unit = { }

    fun String.test(i: Int) {
        i.fo<caret>
    }
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "() for Int in A", typeText: "Unit", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// IGNORE_K2