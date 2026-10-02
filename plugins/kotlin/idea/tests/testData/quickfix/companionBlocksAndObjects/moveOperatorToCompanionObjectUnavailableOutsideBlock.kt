// "Move to companion object" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
// K2_AFTER_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
class Example {
    <caret>operator fun plus(): Int = 1
}
