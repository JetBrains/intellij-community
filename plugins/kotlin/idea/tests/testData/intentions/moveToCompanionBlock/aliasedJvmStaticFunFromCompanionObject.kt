// COMPILER_ARGUMENTS: -Xcompanion-blocks

import kotlin.jvm.JvmStatic as Static

class Foo {
    companion object {
        @Static
        fun f<caret>oo() = 0
    }
}