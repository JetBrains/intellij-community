// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package main

class C {
    fun member() {}

    companion {
        fun bar() {}
    }
}

fun test() {
    C().<caret>
}

// EXIST: member
// EXIST: regularExt
// ABSENT: bar
// ABSENT: baz
