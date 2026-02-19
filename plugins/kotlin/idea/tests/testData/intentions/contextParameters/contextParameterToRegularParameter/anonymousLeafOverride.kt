// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false

interface Context

interface IFaceBase {
    context(_: Context)
    fun foo()
}

interface Iface : IFaceBase {
    context(_: Context)
    override fun foo()
}

class Impl : Iface {
    context(_<caret>: Context)
    override fun foo() {
    }
}
