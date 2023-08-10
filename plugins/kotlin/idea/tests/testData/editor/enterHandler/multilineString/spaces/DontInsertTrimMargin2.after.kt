val a = """blah blah blah
    |<caret>
""".replace(" ", "")?.replace("b", "p").trimMargin().length

// IGNORE_FORMATTER