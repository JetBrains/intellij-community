// "Make 'R' reified and 'flatten' inline" "true"
// WITH_STDLIB
// K2_ERROR: Cannot use 'R' as reified type parameter. Use a class instead.

fun <T: Iterable<Array<R>>, R> T.flatten(): Array<R> {
    return this.flatMap { it.asIterable() }.toTypedArray<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix