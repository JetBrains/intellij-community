// "Implement members" "true"
// WITH_STDLIB
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
annotation class Annotation
interface I {
    @Annotation
    fun foo()
}

<caret>class A : I

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix