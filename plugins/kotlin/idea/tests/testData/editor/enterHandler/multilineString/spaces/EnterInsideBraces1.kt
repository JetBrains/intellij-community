fun some() {
    val b = """
        class Test {
            fun some() {<caret>}
        }
    """.trimIndent()
}

// IGNORE_FORMATTER