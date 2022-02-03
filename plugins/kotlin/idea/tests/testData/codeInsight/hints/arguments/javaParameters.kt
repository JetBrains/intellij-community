fun javaParameters() {
    val some = SomeClass()
    some.invokeMe(/* in */ <hint text="index:"/>0, /* name = */ "me")
    some.invokeMe(/* index = */ 0, /* na = */ <hint text="name:"/>"me")
    some.invokeMe(<hint text="index:"/>0, <hint text="name:"/>"me")
    some.invokeMe(/* index = */ 0, /* name = */ "me")

    some.doNotInvokeMe(/* index = */ 0, /* ...args = */ "a", "b")
    some.doNotInvokeMe(/* index = */ 0, /* ...names = */ <hint text="...args:"/>"a", "b")

    some.singleParamDslWithSameParamName("no hint param name equals method name")
    some.sameFirstParamNameAndVararg("no hint param name equals method name")
    some.sameFirstParamNameAndVararg("no hint param name equals method name", <hint text="...variables:"/>123)
}