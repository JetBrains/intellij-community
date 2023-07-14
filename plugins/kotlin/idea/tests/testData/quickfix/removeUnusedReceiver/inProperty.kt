// "Remove redundant receiver parameter" "true"
val <caret>Any.v: Int
    get() = 123

fun test() {
    "".v
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.UnusedReceiverParameterInspection$RemoveReceiverFix