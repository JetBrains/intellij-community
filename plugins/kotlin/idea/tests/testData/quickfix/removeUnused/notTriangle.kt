// "Safe delete 'something'" "true"

abstract class Abstract {
    open fun <caret>something() = "hi"
}

class Test: Abstract() {
}

// FUS_QUICKFIX_NAME: com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteFix