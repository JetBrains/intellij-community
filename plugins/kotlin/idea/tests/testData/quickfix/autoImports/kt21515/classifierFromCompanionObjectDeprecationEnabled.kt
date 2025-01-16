// "Import class 'FromBarCompanion'" "true"
// LANGUAGE_VERSION: 1.3

package foo

open class Bar {
    companion object {
        class FromBarCompanion
    }
}

class Foo : Bar() {
    val a: <caret>FromBarCompanion? = null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// IGNORE_K2