// "Replace usages of 'typealias OldAlias = A<Int>' in whole project" "true"
// K2_ACTION: "Replace usages of 'OldAlias = A<Int>' in whole project" "true"

package test

import dependency.d.A

fun foo(a: <caret>A<Int>): A<Int>? = null

val usage: A<Int> = A<Int>()
