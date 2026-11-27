// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
class Box
class Ctx(val tag: String)

context(c: Ctx)
operator fun Box.iterator(): Iterator<String> = listOf(c.tag).iterator()

context(<caret>x: Ctx)
fun test(b: Box) {
    for (s in b) println(s)
}
