// "Make 'T' reified and 'flatten' inline" "true"
// WITH_STDLIB

fun <T> Array<Array<T>>.flatten(): Array<T> {
    return this.flatMap { it.asIterable() }.toTypedArray<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReifiedToTypeParameterOfFunctionFix