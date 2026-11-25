// "Implement members" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// Issue: KTIJ-40165
package pack

@RequiresOptIn
annotation class Anno

interface MyInterface {
    @Anno
    context(s: String)
    fun run()
}

class I<caret>mpl : MyInterface {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
