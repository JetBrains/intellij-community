// "Add '@JvmStatic' annotation to 'foo'" "true"
// WITH_STDLIB
open class A {
    companion object {
        protected val foo = 2
    }
}

class B : A() {
    fun bar() {
        print(foo<caret>)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddJvmStaticAnnotationFix