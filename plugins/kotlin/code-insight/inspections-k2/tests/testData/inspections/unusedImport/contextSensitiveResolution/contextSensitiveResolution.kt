package contextSensitiveResolution

import contextSensitiveResolution.Color.RED

enum class Color {
    RED
}

fun take(color: Color) {}

fun usage() {
    val c: Color = RED

    take(RED)

    if (c == RED) {}

    when (c) {
        RED -> {}
    }
}
