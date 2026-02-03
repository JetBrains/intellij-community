// "Implement members" "true"
// WITH_STDLIB
annotation class Annotation
interface I {
    @Annotation
    fun foo()
}

<caret>class A : I

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix