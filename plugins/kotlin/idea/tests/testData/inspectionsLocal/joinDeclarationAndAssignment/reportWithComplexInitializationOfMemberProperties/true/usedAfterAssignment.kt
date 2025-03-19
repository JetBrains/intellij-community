class A {
    <caret>val input: String

    init {
        input = ""
        input.additionalInit()
    }
}

fun String.additionalInit() {}