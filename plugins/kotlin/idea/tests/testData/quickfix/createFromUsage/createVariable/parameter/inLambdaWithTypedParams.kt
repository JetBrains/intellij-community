// "Create parameter 'foo'" "true"

fun test(n: Int) {
    val f = { a: Int, b: Int -> <caret>foo }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix