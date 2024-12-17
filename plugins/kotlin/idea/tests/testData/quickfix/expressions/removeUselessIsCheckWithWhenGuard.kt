// "Remove useless is check" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+WhenGuards
/* Issue: KTIJ-32336 */
/* When guards can't be enabled in the K1 mode, which leads to an error. But the issue still affects it, so the errors are disabled */
// DISABLE-ERRORS

interface A
sealed interface B : A {
    val i: Int
}
interface C : B
interface D : B

private fun dropUselessIs(a: A) {
    a as B
    when (a) {
        <caret>is B if a.i > 5 -> Unit
        is C -> Unit
        is D -> Unit
    }
}
