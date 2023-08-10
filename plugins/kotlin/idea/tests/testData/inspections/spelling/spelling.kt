fun xyzzy(): String {
    // this one is about xyzzy
    val s = "xyzzy ${xyzzy()} \n good\tbad\n"
    /* xyzzy in a block comment */
    /** xyzzy in a documentation comment */
    fun bar() {}
    return """xyzzy in a triple quoted string"""
}

fun injectedLanguage(choice: Boolean): String {
    // language=Regexp
    val validRegex = "[setval]?"
    // there is no injected fragment, spell like plain text
    val nonRegex = "setval"

    return if (choice) validRegex else nonRegex
}