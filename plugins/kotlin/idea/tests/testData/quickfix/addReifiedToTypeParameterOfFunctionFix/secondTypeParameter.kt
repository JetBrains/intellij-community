// "Make 'R' reified and 'flatten' inline" "true"
// WITH_STDLIB

fun <T: Iterable<Array<R>>, R> T.flatten(): Array<R> {
    return this.flatMap { it.asIterable() }.toTypedArray<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix