// "Use inherited visibility" "true"
open class A {
    protected open fun run() {}
}

class B : A() {
    <caret>internal override fun run() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix