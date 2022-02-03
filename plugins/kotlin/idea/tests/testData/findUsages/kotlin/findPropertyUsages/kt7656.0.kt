// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
data class A(val field: Int) {
    val field<caret>: String
}

fun main(args: Array<String>) {
    val a = A(10)

    println(a.field)
}

// DISABLE-ERRORS

// FIR_COMPARISON
