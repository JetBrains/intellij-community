// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package main

class C<T> {
    companion {
        fun bar() {}
    }
}

fun test() {
    C.<caret>
}

// EXIST: baz
// EXIST: ext
