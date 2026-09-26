// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
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
