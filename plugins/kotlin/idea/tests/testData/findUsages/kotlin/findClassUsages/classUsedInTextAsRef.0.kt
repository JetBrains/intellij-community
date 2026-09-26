// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "class Server"

// IGNORE_PLATFORM_JS: KTIJ-29705
// IGNORE_PLATFORM_NATIVE: KTIJ-29705

package server

open class <caret>Server {
    companion object {
        val NAME = "Server"
    }

    open fun work() {
        println("Server")
    }
}

