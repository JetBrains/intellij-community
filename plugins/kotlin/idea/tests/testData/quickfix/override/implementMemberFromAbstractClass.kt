// "Implement members" "true"
// WITH_STDLIB
// K2_ERROR: Class 'B' is not abstract and does not implement abstract base class member:<br>fun foo(): Unit
abstract class A {
    abstract fun foo()
}

<caret>class B : A() {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix