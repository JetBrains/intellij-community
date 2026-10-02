// "Move to companion object" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
// K2_AFTER_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
class Example

companion <caret>operator fun Example.plus(x: Int): Int = x
