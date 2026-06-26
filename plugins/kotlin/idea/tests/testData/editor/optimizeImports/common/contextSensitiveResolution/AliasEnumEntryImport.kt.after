// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

import test.Color.RED as RED_ALIAS

enum class Color {
    RED
}

fun take(color: Color) {}

fun usage() {
    val c: Color = RED_ALIAS

    take(RED_ALIAS)

    if (c == RED_ALIAS) {}

    when (c) {
        RED_ALIAS -> {}
    }
}
