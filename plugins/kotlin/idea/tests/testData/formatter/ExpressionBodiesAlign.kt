class Test {
    fun longName() = 100
    fun short() = 200
    fun tiny() = 300

    fun longLongLong       = 100
    fun longLongLonglongLongLong       =           200

    /**
     * @return 100
     */
    fun withDoc() = 100
    fun withoutDoc() = 200

    fun newLineExpression()
        = "a/b".split("/")
    fun otherNewLineExpression()
        = "a/b".split("/")

    fun longAssignNewLineExpression()
        = "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    fun otherLongAssignNewLineExpression()
        = "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"

    fun longAfterAssignNewLineExpression() =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
    fun otherLongAfterAssignNewLineExpression() =
        "looooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong"
}

// SET_TRUE: ALIGN_IN_COLUMNS_EXPRESSION_BODIES