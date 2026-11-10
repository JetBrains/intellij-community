// "Safe delete 'HELLO'" "true"
import MyEnum.HELLO

enum class MyEnum {
    HELLO<caret>,
}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix