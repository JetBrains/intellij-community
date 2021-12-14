val a =
    ("""
        |  blah blah
        |  blah blah
     """ + """
         <caret>
     """.trimIndent()).trimMargin()

// TODO: Concatenation is not supported

// IGNORE_FORMATTER