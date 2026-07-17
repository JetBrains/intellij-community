// "Use inherited visibility" "true"
// K2_ERROR: CANNOT_WEAKEN_ACCESS_PRIVILEGE
// K2_ERROR: INCOMPATIBLE_MODIFIERS
// K2_ERROR: INCOMPATIBLE_MODIFIERS
open class A {
    protected open fun run() {}
}

class B : A() {
    <caret>private override fun run() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix