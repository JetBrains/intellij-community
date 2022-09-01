class Test {
    fun longName() = 100
    fun short() = 200
    fun tiny() = 300

    fun longNameWithReturnType(): Int = 100
    fun shortWithReturnType(): String = "200"
    fun tinyWithReturnType(): Boolean = true

    fun longLongLong       = 100
    fun longLongLonglongLongLong       =           200

    fun longLongLongWithReturnType: Int       = 100
    fun longLongLonglongLongLongWithReturnType: String      =           "200"

    fun withReturnType(): Int = 100
    fun withoutReturnType() = "200"

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

fun longName() = 100
fun short() = 200
fun tiny() = 300

fun longNameWithReturnType(): Int = 100
fun shortWithReturnType(): String = "200"
fun tinyWithReturnType(): Boolean = true

fun longLongLong       = 100
fun longLongLonglongLongLong       =           200

fun longLongLongWithReturnType: Int       = 100
fun longLongLonglongLongLongWithReturnType: String      =           "200"

fun withReturnType(): Int = 100
fun withoutReturnType() = "200"

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

// SET_TRUE: ALIGN_IN_COLUMNS_EXPRESSION_BODIES