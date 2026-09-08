// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Context

class A {
    context(<caret>_: Context)
    fun fn(): Int = 0

    context(_: Context)
    fun test() {
        val ref: () -> Int = ::fn
    }
}
