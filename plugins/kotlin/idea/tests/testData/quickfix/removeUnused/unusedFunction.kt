// "Safe delete 'unusedFun'" "true"
fun dummy() {
}

fun <caret>unusedFun() {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix