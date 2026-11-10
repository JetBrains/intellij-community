// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    val <caret>size: Int = 1
}

fun fooBound(foo: Foo): () -> Int = foo::size
