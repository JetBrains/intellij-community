// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
interface I {
    context(s: String)
    fun test()
}

class Impl : I {
    context(<caret>s: String)
    override fun test() {
        println("ignores s")
    }
}