// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion {
        fun foo() {
        }
    }

    fun <caret>bar() {
    }

    companion {
        fun baz() {
        }
    }

    companion {
        val boo = ""
    }
}