fun xyzzyy(): String {
    // this one is about xyzzyy
    val s = "xyzzyy ${xyzzyy()} \n good\tbad\n"
    /* xyzzyy in a block comment */
    /** xyzzyy in a documentation comment */
    fun bar() {}
    return """xyzzyy in a triple quoted string"""
}

fun injectedLanguage(choice: Boolean): String {
    // language=Regexp
    val validRegex = "[setval]?"
    // there is no injected fragment, spell like plain text
    val nonRegex = "setval"

    return if (choice) validRegex else nonRegex
}