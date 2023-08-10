// "Replace with 'declaringJavaClass'" "true"
// ACTION: Add method contract to 'getDeclaringClass()'...
// ACTION: Introduce local variable
// ACTION: Put calls on separate lines
// ACTION: Replace with 'declaringJavaClass'
// API_VERSION: 1.7
// WITH_STDLIB

enum class CustomEnum { A; }

fun foo() {
    CustomEnum.A.<caret>declaringClass
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.DeclaringJavaClassMigrationFix