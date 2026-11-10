// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
// WITH_LIBRARY: ../../contextParametersLibrary

import test.library.LibraryBase

class Derived : LibraryBase() {
    context(<caret>s: String)
    override fun foo() {}
}
