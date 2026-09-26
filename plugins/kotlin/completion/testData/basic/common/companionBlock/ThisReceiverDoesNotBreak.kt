// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
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
