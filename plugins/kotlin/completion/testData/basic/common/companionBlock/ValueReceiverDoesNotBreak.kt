// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
class Holder {
    fun instanceMember() {}

    companion {
        fun blockMember() {}
    }
}

companion fun Holder.companionExt() {}

val UpperCaseValue = Holder()

fun main() {
    UpperCaseValue.<caret>
}

// EXIST: instanceMember
// ABSENT: companionExt
// ABSENT: blockMember
