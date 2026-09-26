// "Remove 'operator' modifier" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
class Example {
    companion {
        <caret>operator fun invoke() {}
    }
}