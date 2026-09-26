// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "interface Server"

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

package server

interface <caret>Server {

    companion object {
        @JvmField val ID = ""
        @JvmStatic fun callStatic() {}
    }

}