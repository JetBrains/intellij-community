// COMPILER_ARGUMENTS: -Xcontext-parameters

class Context
class MyOtherContext

context(_: Context, <caret>_: MyOtherContext)
fun Double.foo(p: Int, q: String) {
    useContext()
    useMyClass()
}

context(c: Context)
fun useContext() {
}

context(c: MyOtherContext)
fun useMyClass() {
}
