// "Remove useless '?'" "true"
fun f(a: Int) : Boolean {
    return a is Int?<caret>
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix