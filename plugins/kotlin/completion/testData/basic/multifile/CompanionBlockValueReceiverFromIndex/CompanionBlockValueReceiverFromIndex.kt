// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package main

class Holder {
    fun instanceMember() {}

    companion {
        fun blockMember() {}
    }
}

val UpperCaseValue = Holder()

fun test() {
    UpperCaseValue.<caret>
}

// EXIST: instanceMember
// ABSENT: companionExt
// ABSENT: blockMember
