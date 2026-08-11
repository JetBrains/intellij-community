// "Add secondary constructor to 'Foo'" "true"
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_AFTER_ERROR: NONE_APPLICABLE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: UNRESOLVED_REFERENCE

class Foo(
    val name: String,
) {
    companion object {
        fun of(number: Number): Foo {
            return Foo(<caret>number is xxx.bar())
        }
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix