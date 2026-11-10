// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class Base

class Foo {
    companion object : Base() {
        fun f<caret>oo() {
        }
    }
}

val asBase: Base = Foo
