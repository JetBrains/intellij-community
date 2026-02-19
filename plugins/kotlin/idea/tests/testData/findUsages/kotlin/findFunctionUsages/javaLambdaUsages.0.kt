// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "fun f(): Unit"
interface X {
    fun <caret>f()
}

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code
