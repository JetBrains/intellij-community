// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// K2_ERROR:

open class A {
    context(a: A)
    open fun m() {}
}

class B: A() {
    conte<caret>xt(a: A)
    override fun m() {
        super.m()
    }
}