// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages
class WithSuspend {
    suspend operator fun invo<caret>ke() {}
}

suspend fun main() {
    val a = WithSuspend()

    a()
    a.invoke()
}


