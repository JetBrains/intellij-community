// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
class Context

class A {
    context(_: Context)
    fun fn(): Int = 0

    context(<caret>_: Context)
    fun test() {
        val ref: () -> Int = ::fn
    }
}
