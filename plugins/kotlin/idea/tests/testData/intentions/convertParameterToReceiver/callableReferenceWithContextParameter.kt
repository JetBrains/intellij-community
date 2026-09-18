// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

interface Context

context(_: Context)
fun foo(<caret>s: String): Int = 0

context(_: Context)
fun test() {
    val ref: (String) -> Int = ::foo
}