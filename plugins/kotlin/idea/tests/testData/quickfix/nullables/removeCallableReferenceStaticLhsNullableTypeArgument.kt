// "Remove '?'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: INVALID_QUALIFIER_IN_LHS_OF_CALLABLE_REFERENCE_TO_STATIC_ERROR
// K2_AFTER_ERROR: INVALID_QUALIFIER_IN_LHS_OF_CALLABLE_REFERENCE_TO_STATIC_ERROR
// ACTION: Remove type arguments
class G<A> {
    companion {
        fun foo() {}
    }
}

fun test() {
    G<String<caret>?>::foo
}
