// "Implement members" "true"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO

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