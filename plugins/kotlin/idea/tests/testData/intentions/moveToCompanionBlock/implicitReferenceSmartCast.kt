// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Callable reference won't be processed: ::barMember

open class Foo {
    fun <caret>len(xs: List<Int>): List<Int> = if (this is Bar) xs.map(::barMember) else xs
}

class Bar : Foo() { fun barMember(x: Int) = x }
