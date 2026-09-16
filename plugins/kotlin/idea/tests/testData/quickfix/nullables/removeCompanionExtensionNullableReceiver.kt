// "Remove '?'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: COMPANION_EXTENSION_NULLABLE_RECEIVER
class C

companion fun C<caret>?.incorrect7() {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix
