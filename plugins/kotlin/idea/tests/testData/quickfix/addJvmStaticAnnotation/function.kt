// "Add '@JvmStatic' annotation to 'foo'" "true"
// WITH_STDLIB
// K2_ERROR: Using protected members that are not '@JvmStatic' in the superclass companion is not yet supported.
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