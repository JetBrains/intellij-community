// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Context

interface IFaceBase {
    context(<caret>_: Context)
    fun foo()
}

class Impl : IFaceBase {
    context(_: Context)
    override fun foo() {
        useContextImplicitly()
    }
}

context(c: Context)
fun useContextImplicitly() {}
