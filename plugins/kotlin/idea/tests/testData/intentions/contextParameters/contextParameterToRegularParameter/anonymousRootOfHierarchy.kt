// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Context

interface IFaceBase {
    context(<caret>_: Context)
    fun foo()
}

interface Iface : IFaceBase {
    context(_: Context)
    override fun foo()
}

class Impl : Iface {
    context(_: Context)
    override fun foo() {
    }
}
