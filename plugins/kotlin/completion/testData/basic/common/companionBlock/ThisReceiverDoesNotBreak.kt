// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class C {
    fun member() {}

    companion {
        fun blockMember() {}
    }

    fun test() {
        this.<caret>
    }
}

companion fun C.companionExt() {}

// EXIST: member
// ABSENT: companionExt
// ABSENT: blockMember
