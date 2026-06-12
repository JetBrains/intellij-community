// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C {
    companion {
        fun bar() {}
    }
}

companion fun C.baz() {}

fun main() {
    C.ba<caret>
}

// EXIST: { itemText: "bar", attributes: "bold" }
// EXIST: { itemText: "baz", attributes: "bold" }
