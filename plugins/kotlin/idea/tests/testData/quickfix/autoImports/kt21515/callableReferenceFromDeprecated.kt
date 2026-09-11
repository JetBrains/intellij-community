// "Import class 'FromBarCompanion'" "true"
// LANGUAGE_VERSION: 1.3
// K2_ERROR: UNRESOLVED_REFERENCE

package foo

open class Bar {
    companion object {
        class FromBarCompanion {
            fun foo() = 42
        }
    }
}

class Foo : Bar() {
    val a = <caret>FromBarCompanion::foo
}
// IGNORE_K2