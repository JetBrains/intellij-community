import java.lang.System

// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class Foo {
    fun getFive(){
        return 5
    }
    fun <caret>off(a){

    }

    fun m() {
        java.lang.System.out.println(off(1))
        off(1)
        off(1)
        if (off(getFive())) {

        }
    }
}