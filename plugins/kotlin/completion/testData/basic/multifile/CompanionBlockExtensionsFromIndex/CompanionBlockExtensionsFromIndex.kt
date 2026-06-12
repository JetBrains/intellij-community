// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package main

class C {
    companion {
        fun bar() {}
    }
}

fun test() {
    C.<caret>
}

// EXIST: baz
// EXIST: ext
