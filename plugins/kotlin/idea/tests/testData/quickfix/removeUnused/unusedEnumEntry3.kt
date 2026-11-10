// "Safe delete 'HELLO'" "true"
enum class MyEnum {
    HELLO<caret>
}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix