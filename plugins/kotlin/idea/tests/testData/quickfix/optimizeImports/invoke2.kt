// "Optimize imports" "true"
// WITH_STDLIB
package ppp

import ppp.invoke<caret>

object Bar
object Foo {
    // val bar = Bar
}

fun Foo.bar() = 1
operator fun Bar.invoke() = 2

fun main() {
    println(Foo.bar())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.KotlinOptimizeImportsQuickFix