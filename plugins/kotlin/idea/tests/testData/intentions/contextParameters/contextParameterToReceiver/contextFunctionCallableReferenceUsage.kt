// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

interface Context

context(_<caret>: Context)
fun fn(): Int = 0

context(_: Context)
fun test() {
    print(::fn)
}
