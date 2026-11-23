// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Callable reference won't be processed: Foo::size

class Foo {
    val <caret>size: Int = 1
}

val b8Unbound: (Foo) -> Int = Foo::size
