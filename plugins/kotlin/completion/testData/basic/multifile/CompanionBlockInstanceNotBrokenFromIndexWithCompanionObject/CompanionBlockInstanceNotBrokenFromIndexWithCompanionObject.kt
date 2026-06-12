// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package main

class C {
    fun member() {}

    companion {
        fun bar() {}
    }

    companion object {
        fun coFun() {}
        val coVal = 1
    }
}

fun test() {
    C().<caret>
}

// EXIST: member
// EXIST: regularExt
// ABSENT: bar
// ABSENT: baz
// ABSENT: coFun
// ABSENT: coVal
