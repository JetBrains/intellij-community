// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution

package test

import some.fake.RED

class MyColor(val name: String) {
    companion object {
        val RED = MyColor("red")
    }
}

fun take(color: MyColor) {}

fun usage() {
    val c: MyColor = RED

    if (c == RED) {}

    when (c) {
        RED -> {}
    }
}
