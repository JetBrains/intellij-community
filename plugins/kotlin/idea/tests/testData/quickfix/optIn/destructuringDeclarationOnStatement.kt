// "Opt in for 'MyOptIn' on statement" "false"
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR
// K2_AFTER_ERROR: OPT_IN_USAGE_ERROR

@RequiresOptIn
annotation class MyOptIn

@MyOptIn
data class OptInData(val a: String, val b: String)

fun reproduceIssue() {
    val (x, y) = <caret>OptInData("1", "2")
}

