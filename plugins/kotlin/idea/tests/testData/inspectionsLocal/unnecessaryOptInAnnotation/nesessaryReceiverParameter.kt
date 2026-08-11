// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn


@RequiresOptIn
annotation class Anno1

@Anno1
interface Foo3

@OptIn(Anno1::class)
class Bar : Foo3

@OptIn(Anno1::class)
fun Foo3.foo() {}

@OptIn(Ann<caret>o1::class)
fun Bar.check() {
    foo()
}