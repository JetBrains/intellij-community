// WITH_STDLIB
// PROBLEM: none
package my.simple.name

fun main() {
    val a = kotlin.Int<caret>.Companion.MAX_VALUE
}

// IGNORE_FIR

// K2 RemoveRedundantQualifierNameInspection changes it to Int.Companion.MAX_VALUE.