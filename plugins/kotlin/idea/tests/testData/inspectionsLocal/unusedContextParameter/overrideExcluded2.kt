// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
interface I {
    context(<caret>s: String)
    fun test()
}

class Impl : I {
    context(s: String)
    override fun test() {
        println("ignores s")
    }
}