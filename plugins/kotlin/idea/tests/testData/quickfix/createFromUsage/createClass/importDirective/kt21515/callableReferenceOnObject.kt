// "Add explicit import" "true"
// LANGUAGE_VERSION: 1.2

open class Bar {
    companion object {
        object FromBarCompanion {
            fun foo() = 42
        }
    }
}

class Foo : Bar() {
    val a = <caret>FromBarCompanion::foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExplicitImportForDeprecatedVisibilityFix