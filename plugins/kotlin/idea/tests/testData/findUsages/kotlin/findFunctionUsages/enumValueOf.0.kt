// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages

enum class MyEnum {
    A, B
}

fun test() {
    MyEnum.valueOf<caret>("A")
}