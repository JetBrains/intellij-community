// "Implement members" "true"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED_BY_ENUM_ENTRY

interface KtInterface {
    fun foo()
    fun bar() {}
}

enum class KtEnumClass : KtInterface {
    ENUM_ENTRY<caret>;

    abstract fun saySomething(): String
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix