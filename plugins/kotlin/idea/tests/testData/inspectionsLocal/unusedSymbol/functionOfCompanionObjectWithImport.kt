// PROBLEM: none
// WITH_STDLIB
package one

import one.My124.Companion.impCost

class My124() {
    companion object {
        fun impCost<caret>() = 42
    }
}

fun tete4() {
    println(impCost())
}