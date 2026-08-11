// "Add '@JvmStatic' annotation to 'foo'" "true"
// WITH_STDLIB
// K2_ERROR: SUBCLASS_CANT_CALL_COMPANION_PROTECTED_NON_STATIC
open class A {
    companion object {
        protected fun foo() = 2
    }
}

class B : A() {
    fun bar() {
        print(<caret>foo())
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmStaticAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmStaticAnnotationFix