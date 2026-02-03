// "Add explicit import" "true"
// LANGUAGE_VERSION: 1.2

open class Bar {
    companion object {
        class FromBarCompanion
    }
}

class Foo : Bar() {
    val a = <caret>FromBarCompanion()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExplicitImportForDeprecatedVisibilityFix