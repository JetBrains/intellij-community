// "Remove single lambda parameter declaration" "true"
fun test() {
    val f: (Int) -> Unit = { <caret>i: Int -> foo() }
    bar(f)
}

fun foo() {}
fun bar(f: (Int) -> Unit) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSingleLambdaParameterFix