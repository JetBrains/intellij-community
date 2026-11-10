// "Safe delete 'unusedFun'" "true"
fun dummy() {
}

fun <caret>unusedFun() {

}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix