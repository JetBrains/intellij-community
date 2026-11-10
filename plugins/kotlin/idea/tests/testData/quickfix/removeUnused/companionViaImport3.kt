// "Safe delete 'NamedObject'" "true"
import TestClass.NamedObject

class TestClass{
    private object NamedObject<caret> {
        const val CONST = "abc"
    }
}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix