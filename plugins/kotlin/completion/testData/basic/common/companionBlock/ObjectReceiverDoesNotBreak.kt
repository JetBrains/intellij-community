// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
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
