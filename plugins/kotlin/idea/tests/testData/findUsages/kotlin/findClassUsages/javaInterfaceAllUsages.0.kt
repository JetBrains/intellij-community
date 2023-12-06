// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "interface Server"
package server

interface <caret>Server {

    companion object {
        @JvmField val ID = ""
        @JvmStatic fun callStatic() {}
    }

}