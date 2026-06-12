// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C {
    companion {
        fun bar() {}
    }
}

companion fun C.baz() {}
companion val C.ext: Int get() = 1

fun main() {
    C.<caret>
}

// EXIST: baz
// EXIST: ext
