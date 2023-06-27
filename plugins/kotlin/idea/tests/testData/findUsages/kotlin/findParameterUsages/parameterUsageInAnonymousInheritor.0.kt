// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "param: T"

interface MyInterface {
    fun<T> myFun(<caret>param: T)
}


val d = object : MyInterface {
    override fun <T> myFun(param: T) {
        val param2 = param
    }
}