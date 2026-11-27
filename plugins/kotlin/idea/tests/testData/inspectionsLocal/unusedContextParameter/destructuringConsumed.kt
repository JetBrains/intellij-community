// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
class Box
class Ctx(val tag: String)

context(c: Ctx)
operator fun Box.component1(): String = "c1:${c.tag}"

context(c: Ctx)
operator fun Box.component2(): String = "c2:${c.tag}"

context(<caret>x: Ctx)
fun test(b: Box) {
    val (p, q) = b
    println(p + q)
}
