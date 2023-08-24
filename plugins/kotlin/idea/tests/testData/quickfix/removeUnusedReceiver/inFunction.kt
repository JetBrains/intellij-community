// "Remove redundant receiver parameter" "true"
fun <caret>Any.foo() {

}

fun test() {
    1.foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.UnusedReceiverParameterInspection$RemoveReceiverFix