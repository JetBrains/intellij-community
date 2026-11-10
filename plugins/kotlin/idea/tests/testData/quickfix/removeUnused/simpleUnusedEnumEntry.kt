// "Safe delete 'WORLD'" "true"
enum class MyEnum {
    HELLO,
    WORLD<caret>
}

fun main() {
    MyEnum.HELLO
}
// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix