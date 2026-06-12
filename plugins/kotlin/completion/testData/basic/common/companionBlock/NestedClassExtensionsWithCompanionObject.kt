// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class Outer {
    class Nested {
        companion {
            fun bar() {}
        }

        companion object {
            fun coFun() {}
            val coVal = 1
        }
    }
}

companion fun Outer.Nested.baz() {}
companion val Outer.Nested.ext: Int get() = 1

fun main() {
    Outer.Nested.<caret>
}

// EXIST: baz
// EXIST: ext
// EXIST: coFun
// EXIST: coVal
