// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
// Issue: KTIJ-24074

class Ctx

interface A {
    context(c: Ctx)
    fun foo()
}

class<caret> B : A {

}
