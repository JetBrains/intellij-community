// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// PROBLEM: none

@RequiresOptIn
annotation class MyAnno

open class Foo @MyAnno constructor()

@OptIn(<caret>MyAnno::class)
class Bar : Foo()
