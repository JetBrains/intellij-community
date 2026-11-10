// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
// WITH_LIBRARY: ../../contextParametersLibrary

import test.library.LibraryBase

interface WritableInterface {
    context(s: String)
    fun foo()
}

open class Middle : LibraryBase(), WritableInterface {
    context(s: String)
    override fun foo() {}
}

class Leaf : Middle() {
    context(<caret>s: String)
    override fun foo() {}
}
