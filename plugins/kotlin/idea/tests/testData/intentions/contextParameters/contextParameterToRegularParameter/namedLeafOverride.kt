// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Context

interface IFaceBase {
    context(c: Context)
    fun foo()
}

interface Iface : IFaceBase {
    context(c: Context)
    override fun foo()
}

class Impl : Iface {
    context(<caret>c: Context)
    override fun foo() {
    }
}
