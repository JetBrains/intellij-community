// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
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
