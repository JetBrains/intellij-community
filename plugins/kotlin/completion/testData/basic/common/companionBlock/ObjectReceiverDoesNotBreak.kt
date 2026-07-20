// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
open class Base {
    fun baseMember() {}
}

object Obj : Base() {
    fun objMember() {}
}

companion fun Base.companionExt() {}

fun main() {
    Obj.<caret>
}

// EXIST: objMember
// EXIST: baseMember
// ABSENT: companionExt
