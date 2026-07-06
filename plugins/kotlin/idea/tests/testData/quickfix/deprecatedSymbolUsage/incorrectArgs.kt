// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix" "false"
// ERROR: The integer literal does not conform to the expected type Array<out String>
// ERROR: Assigning single elements to varargs in named form is forbidden
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION_ERROR
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION_ERROR

@Deprecated("", ReplaceWith("newFun()", imports = 123))
fun oldFun() {
    newFun()
}

fun newFun(){}

fun foo() {
    <caret>oldFun()
}
