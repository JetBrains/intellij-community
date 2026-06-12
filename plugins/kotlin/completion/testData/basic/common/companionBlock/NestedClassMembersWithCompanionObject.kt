// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class Outer {
    class Nested {
        companion {
            fun bar() {}
            val prop = 1
        }

        companion object {
            fun coFun() {}
            val coVal = 1
        }
    }
}

fun main() {
    Outer.Nested.<caret>
}

// EXIST: bar
// EXIST: prop
// EXIST: coFun
// EXIST: coVal
