package a

val variableCall: () -> Unit = {}
val extensionVariableCall1: Int.() -> Unit = {}
val extensionVariableCall2: Int.() -> Unit = {}

<selection>fun Int.test(n: Int) {
    variableCall()
    extensionVariableCall1()
    n.extensionVariableCall2()
}</selection>
