// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        fun f<caret>oo() {
        }
    }
}

val companion: Foo.Companion = Foo.Companion
