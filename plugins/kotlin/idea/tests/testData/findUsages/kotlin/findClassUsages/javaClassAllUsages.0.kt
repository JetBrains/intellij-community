// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class Server"
package testing

class <caret>Server() {

    companion object {
        @JvmField var ID = ""
        @JvmStatic fun callStatic() {}
    }

}

