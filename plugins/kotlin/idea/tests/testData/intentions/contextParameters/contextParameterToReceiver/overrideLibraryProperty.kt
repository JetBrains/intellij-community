// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
// WITH_LIBRARY: ../../contextParametersLibrary

import test.library.LibraryProperty

class Derived : LibraryProperty() {
    context(<caret>s: String)
    override val value: Int
        get() = s.length
}
