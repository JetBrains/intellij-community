// COMPILER_ARGUMENTS: -Xcontext-parameters
class Context
open class Boo {
    context(ct<caret>x: Context)
    open fun foo() {
    }
}

class Baz : Boo() {
    context(ctx: Context)
    override fun foo() {
        super.foo()
    }
}