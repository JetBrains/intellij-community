// "Implement members" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// Issue: KTIJ-39805

interface I<T> {
    context(_: Int)
    fun f(): List<T>
}

class<caret> X : I<Int> {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
