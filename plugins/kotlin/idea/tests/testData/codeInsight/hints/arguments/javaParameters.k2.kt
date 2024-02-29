fun javaParameters() {
    val some = SomeClass()
    some.invokeMe(/* in */ /*<# [javaParameters.dependency.java:50]index|: #>*/<hint text="index:"/>0, /* name = */ "me")
    some.invokeMe(/* index = */ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* na = */ /*<# [javaParameters.dependency.java:61]name|: #>*/<hint text="name:"/>"me")
    some.invokeMe(/*<# [javaParameters.dependency.java:50]index|: #>*/<hint text="index:"/>0, <hint text="name:"/>"me")
    some.invokeMe(/* index = */ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")

    some.invokeMe(/*index = */ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")
    some.invokeMe(/* index= */ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")
    some.invokeMe(/* index=*/ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")
    some.invokeMe(/* index =*/ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")
    some.invokeMe(/*   index   =   */ /*<# [javaParameters.dependency.java:50]index|: #>*/0, /* name = */ /*<# [javaParameters.dependency.java:61]name|: #>*/"me")

    some.doNotInvokeMe(/* index = */ /*<# [javaParameters.dependency.java:114]index|: #>*/0, /* ...args = */ /*<# …|[javaParameters.dependency.java:125]args|: #>*/"a", "b")
    some.doNotInvokeMe(/* index = */ /*<# [javaParameters.dependency.java:114]index|: #>*/0, /* ...names = */ /*<# …|[javaParameters.dependency.java:125]args|: #>*/<hint text="...args:"/>"a", "b")

    some.singleParamDslWithSameParamName(/*<# [javaParameters.dependency.java:200]singleParamDslWithSameParamNam...|: #>*/"no hint param name equals method name")
    some.sameFirstParamNameAndVararg(/*<# [javaParameters.dependency.java:294]sameFirstParamNameAndVararg|: #>*/"no hint param name equals method name")
    some.sameFirstParamNameAndVararg(/*<# [javaParameters.dependency.java:294]sameFirstParamNameAndVararg|: #>*/"no hint param name equals method name", /*<# …|[javaParameters.dependency.java:330]variables|: #>*/<hint text="...variables:"/>123)
}