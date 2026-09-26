// "Replace with '@JvmName'" "false"
// WITH_STDLIB
// Replacing would produce a second @JvmName on the same declaration.
@file:OptIn(ExperimentalStdlibApi::class)

@JvmName("bar")
@JvmE<caret>xposeBoxed("fooJvm")
fun foo(x: Int) {}
