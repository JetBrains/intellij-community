fun foo() {
    // cases without type
    fun longName() = 100
    fun short() = 200
    fun tiny() = 300

    // cases with type
    fun longWithReturnType(): Int = 100
    fun shortWithReturnType(): String = "200"
    fun tinyWithReturnType(): Boolean = true

    // with type and without
    fun withReturnType(): Int = 100
    fun withoutReturnType() = "200"

    // without type beetwen typed ones
    fun withType1(): Int = 100
    fun withoutType1() = "200"
    fun withType21(): String = "100"

    // with kdoc and without
    /**
     * @return 100
     */
    fun withDoc(): Int = 100
    fun withoutDoc(): String = 200

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

// SET_TRUE: ALIGN_IN_COLUMNS_LOCAL_EXPRESSION_BODIES