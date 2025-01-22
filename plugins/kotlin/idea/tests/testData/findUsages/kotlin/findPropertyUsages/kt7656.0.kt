// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val field: String"
data class A(val field: Int) {
    val field<caret>: String
}

fun main(args: Array<String>) {
    val a = A(10)

    println(a.field)
}

// DISABLE_ERRORS


