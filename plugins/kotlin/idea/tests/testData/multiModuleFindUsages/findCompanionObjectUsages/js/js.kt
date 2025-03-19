// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, expected
actual class Clazz006<caret> {
    actual companion object {
        actual val AA_A: Int
            get() = TODO("Not yet implemented")
    }
}


fun useCompanionInJs() {
    println(Clazz006.AA_A)
}

// IGNORE_K1
