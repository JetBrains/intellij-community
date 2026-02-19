fun bar(someThing: Boolean) {}

class A {
    fun func(parameter: String): String = "hello from A"
}

fun A.extension(parameter: Int): Int = 0

fun foo(action: (x: Int, y: String) -> String) {
    action(/*<# x| = #>*/1, /*<# y| = #>*/"1")
    action.invoke(/*<# x| = #>*/1, /*<# y| = #>*/"1")

    val actionRef = action
    actionRef(/*<# x| = #>*/2, /*<# y| = #>*/"2")
    actionRef.invoke(/*<# x| = #>*/2, /*<# y| = #>*/"2")

    val barRef = ::bar
    barRef(/*<# someThing| = #>*/true)
    barRef.invoke(/*<# someThing| = #>*/true)

    val aFuncRef = A::func
    aFuncRef(A(), /*<# parameter| = #>*/"text")
    aFuncRef.invoke(A(), /*<# parameter| = #>*/"text")

    val aExtRef = A::extension
    aExtRef(A(), /*<# parameter| = #>*/0)
    aExtRef.invoke(A(), /*<# parameter| = #>*/0)
}