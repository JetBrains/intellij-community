fun bar(someThing: Boolean) {}

class A {
    fun func(parameter: String): String = "hello from A"
}

fun A.extension(parameter: Int): Int = 0

fun foo(action: (x: Int, y: String) -> String) {
    action(<hint text="x:"/>1, <hint text="y:"/>"1")
    action.invoke(<hint text="x:"/>1, <hint text="y:"/>"1")

    val actionRef = action
    actionRef(<hint text="x:"/>2, <hint text="y:"/>"2")
    actionRef.invoke(<hint text="x:"/>2, <hint text="y:"/>"2")

    val barRef = ::bar
    barRef(<hint text="someThing:"/>true)
    barRef.invoke(<hint text="someThing:"/>true)

    val aFuncRef = A::func
    aFuncRef(A(), <hint text="parameter:"/>"text")
    aFuncRef.invoke(A(), <hint text="parameter:"/>"text")

    val aExtRef = A::extension
    aExtRef(A(), <hint text="parameter:"/>0)
    aExtRef.invoke(A(), <hint text="parameter:"/>0)
}