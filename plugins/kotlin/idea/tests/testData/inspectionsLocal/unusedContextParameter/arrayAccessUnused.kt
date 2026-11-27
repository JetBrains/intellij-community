// COMPILER_ARGUMENTS: -Xcontext-parameters
class Box
class Ctx

operator fun Box.get(i: Int): Int = i

context(<caret>x: Ctx)
fun test(b: Box): Int = b[0]
