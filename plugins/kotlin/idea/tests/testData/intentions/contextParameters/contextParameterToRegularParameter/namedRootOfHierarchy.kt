// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Context

interface IFaceBase {
    context(<caret>c: Context)
    fun foo()
}

interface Iface : IFaceBase {
    context(c: Context)
    override fun foo()
}

class Impl : Iface {
    context(c: Context)
    override fun foo() {
    }
}
