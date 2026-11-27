// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
class Box
class Ctx(val tag: String)

context(c: Ctx)
operator fun Box.get(i: Int): String = c.tag + i

context(<caret>x: Ctx)
fun test(b: Box): String = b[0]
