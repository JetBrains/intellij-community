val a = """blah blah blah
    <caret>
""".replace(" ", "")?.replace("b", "p").trimIndent().length

// IGNORE_FORMATTER