// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C {
    companion {
        fun bar() {}
        val prop = 1
    }

    companion object {
        fun coFun() {}
        val coVal = 1
    }
}

fun main() {
    C.<caret>
}

// EXIST: bar
// EXIST: prop
// EXIST: coFun
// EXIST: coVal
