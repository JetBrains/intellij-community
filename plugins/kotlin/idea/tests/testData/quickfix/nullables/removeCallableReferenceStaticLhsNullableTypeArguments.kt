// "Remove type arguments" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: INVALID_QUALIFIER_IN_LHS_OF_CALLABLE_REFERENCE_TO_STATIC_ERROR
// K2_AFTER_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS
// ACTION: Remove '?'
class G<A> {
    companion {
        fun foo() {}
    }
}

fun test() {
    G<String<caret>?>?::foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveCallableReferenceStaticLhsFixFactories$RemoveCallableReferenceStaticLhsFix
