// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
class C<T> {
    companion {
        fun bar() {}
        val prop = 1
    }
}

fun main() {
    C.<caret>
}

// EXIST: bar
// EXIST: prop
