// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// LANGUAGE_VERSION: 2.2
// SHOULD_FAIL_WITH: A function in a companion block cannot have a receiver

class Foo {
    companion {
        fun foo(r<caret>aw: String): String = raw.trim()
    }
}