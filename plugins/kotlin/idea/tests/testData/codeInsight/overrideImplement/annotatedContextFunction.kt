// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
// Issue: KTIJ-40165
package pack

@RequiresOptIn
annotation class Anno

interface MyInterface {
    @Anno
    context(s: String)
    fun run()
}

class I<caret>mpl : MyInterface {

}
