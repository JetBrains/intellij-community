// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package main

import dependency.C

fun test() {
    C.<caret>
}

// EXIST: bar
// EXIST: prop
// EXIST: coFun
// EXIST: coVal
