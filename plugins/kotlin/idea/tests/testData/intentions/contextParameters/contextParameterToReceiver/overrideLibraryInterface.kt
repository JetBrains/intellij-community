// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
// WITH_LIBRARY: ../../contextParametersLibrary

import test.library.LibraryInterface

class Derived : LibraryInterface {
    context(<caret>s: String)
    override fun foo() {}
}
