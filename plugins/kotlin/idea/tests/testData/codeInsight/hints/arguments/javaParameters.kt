fun javaParameters() {
    val some = SomeClass()
    some.invokeMe(<hint text="index:"/>0, <hint text="name:"/>"me")
    some.invokeMe(/* index = */ 0, /* name = */ "me")
}