// "Replace the receiver with 'Example'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
class Example

companion fun Example.baz() {}

fun m(e: Example) {
    e.b<caret>az()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceInstanceReceiverWithClassNameFix
