// FIR_COMPARISON

enum class Foo {
    BAR
}

fun foo() {
    Foo.BA<caret>
}

// EXIST: { itemText: "BAR", lookupString: "BAR", tailText: " (Foo)", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg", attributes: "" }