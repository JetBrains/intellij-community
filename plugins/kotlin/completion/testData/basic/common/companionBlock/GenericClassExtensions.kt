// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C<T> {
    companion {
        fun bar() {}
    }
}

companion fun <T> C<T>.baz() {}
companion val <T> C<T>.ext: Int get() = 1

fun main() {
    C.<caret>
}

// EXIST: baz
// EXIST: ext
