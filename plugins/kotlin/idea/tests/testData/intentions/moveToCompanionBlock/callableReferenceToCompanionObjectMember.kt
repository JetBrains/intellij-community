// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        fun <caret>len(x: Int) = x
    }
}

val companionReference: (Int) -> Int = Foo::len
