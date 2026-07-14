// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
package main

import dependency.C

fun test() {
    C.<caret>
}

// EXIST: bar
// EXIST: prop
// EXIST: coFun
// EXIST: coVal
