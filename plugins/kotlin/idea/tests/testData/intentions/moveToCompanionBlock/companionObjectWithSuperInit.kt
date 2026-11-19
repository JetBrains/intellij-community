// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class Base {
    init { println("registered") }
}

class A {
    companion object : Base() {
        fun f<caret>oo() {}
    }
}

fun main() {
    A()
}