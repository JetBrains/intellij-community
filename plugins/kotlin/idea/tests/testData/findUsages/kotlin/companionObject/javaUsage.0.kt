// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages

class Foo {
    companion <caret>object {
        fun f() {
        }

        @JvmStatic
        fun s() {
        }

        const val CONST = 42
    }
}

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code
