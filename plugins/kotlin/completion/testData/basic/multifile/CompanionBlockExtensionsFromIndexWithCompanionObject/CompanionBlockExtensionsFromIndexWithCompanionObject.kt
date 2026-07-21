// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package main

class C {
    companion {
        fun bar() {}
    }

    companion object {
        fun coFun() {}
        val coVal = 1
    }
}

fun test() {
    C.<caret>
}

// EXIST: baz
// EXIST: ext
// EXIST: coFun
// EXIST: coVal
