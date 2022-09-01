class Test {
    val longName = 100
    val short = 200
    val tiny = 300

    val longNameWithReturnType: Int = 100
    val shortWithReturnType: String = "200"
    val tinyWithReturnType: Boolean = true

    val longLongLong       = 100
    val longLongLonglongLongLong       =           200

    val longLongLongWithReturnType: Int       = 100
    val longLongLonglongLongLongWithReturnType: String      =           "200"

    val withType: Int = 100
    val withoutType = "200"

    /**
     * @return 100
     */
    val withDoc: Int = 100
    val withoutDoc: String = 200

    val newLineExpression
        = "a/b".split("/")
    val otherNewLineExpression
        = "a/b".split("/")

    val longAssignNewLineExpression
        = "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    val otherLongAssignNewLineExpression
        = "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"

    val longAfterAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    val otherLongAfterAssignNewLineExpression =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
}

// SET_TRUE: ALIGN_IN_COLUMNS_PROPERTIES