// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C {
    fun member() {}

    companion {
        fun bar() {}
        val prop = 1
    }
}

companion fun C.baz() {}
fun C.regularExt() {}

fun main() {
    C().<caret>
}

// EXIST: member
// EXIST: regularExt
// ABSENT: bar
// ABSENT: prop
// ABSENT: baz
