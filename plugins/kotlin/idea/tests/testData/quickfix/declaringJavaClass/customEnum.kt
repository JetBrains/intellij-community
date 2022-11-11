// "Replace with 'declaringJavaClass'" "false"
// ACTION: Add method contract to 'getDeclaringClass()'
// ACTION: Introduce local variable
// API_VERSION: 1.7
// WITH_STDLIB

enum class CustomEnum { A; }

fun foo() {
    CustomEnum.A.<caret>declaringClass // no quick-fix, but should be (KT-53807)
}