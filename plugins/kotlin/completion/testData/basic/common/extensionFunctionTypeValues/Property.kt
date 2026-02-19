// FIR_COMPARISON
class A {
    val foo: Int.() -> Unit = { }

    fun String.test(i: Int) {
        i.fo<caret>
    }
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "()", typeText: "Unit", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg"}