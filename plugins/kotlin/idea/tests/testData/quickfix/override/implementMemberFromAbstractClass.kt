// "Implement members" "true"
// WITH_STDLIB
abstract class A {
    abstract fun foo()
}

<caret>class B : A() {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix