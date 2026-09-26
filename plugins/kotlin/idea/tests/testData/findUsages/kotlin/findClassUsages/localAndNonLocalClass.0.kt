// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class C"


class <caret>C {
    init {
        println("global")
    }
}

fun main(args: Array<String>) {
    C()
    class C {
        init {
            println("local")
        }
    }
    C()
}