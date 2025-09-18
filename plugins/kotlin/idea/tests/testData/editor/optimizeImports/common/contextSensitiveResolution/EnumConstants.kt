// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
// IGNORE_K1
package test

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

