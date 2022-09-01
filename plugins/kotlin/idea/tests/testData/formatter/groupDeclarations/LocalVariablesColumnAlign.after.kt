fun foo() {
    // cases without type
    val longName = 100
    val short    = 200
    val tiny     = 300

    // cases with type
    val longWithReturnType:  Int     = 100
    val shortWithReturnType: String  = "200"
    val tinyWithReturnType:  Boolean = true

    // inside nested block
    if (1) {
        // with type and without
        val withType: Int = 100
        val withoutType   = "200"

        // without type beetwen typed ones
        val withType1:  Int    = 100
        val withoutType1       = "200"
        val withType21: String = "100"
    }

    // with kdoc and without
    /**
     * @return 100
     */
    val withDoc:    Int    = 100
    val withoutDoc: String = 200

    val newLineExpression = "a/b".split("/")
    val otherNewLineExpression = "a/b".split("/")

    val longAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    val otherLongAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"

    val longAfterAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    val otherLongAfterAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
}

// SET_TRUE: ALIGN_IN_COLUMNS_LOCAL_PROPERTIES
