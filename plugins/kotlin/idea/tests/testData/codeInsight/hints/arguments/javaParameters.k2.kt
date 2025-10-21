fun javaParameters() {
    val some = SomeClass()
    some.invokeMe(/* in */ /*<# [javaParameters.dependency.java:54]index| = #>*/0, /* name = */ "me")
    some.invokeMe(/* index = */ 0, /* na = */ /*<# [javaParameters.dependency.java:68]name| = #>*/"me")
    some.invokeMe(/*<# [javaParameters.dependency.java:54]index| = #>*/0, /*<# [javaParameters.dependency.java:68]name| = #>*/"me")
    some.invokeMe(/* index = */ 0, /* name = */ "me")

    some.invokeMe(/*index = */ 0, /* name = */ "me")
    some.invokeMe(/* index= */ 0, /* name = */ "me")
    some.invokeMe(/* index=*/ 0, /* name = */"me")
    some.invokeMe(/* index =*/ 0, /* name = */ "me")
    some.invokeMe(/*   index   =   */ 0, /* name = */ "me")

    some.doNotInvokeMe(/* index= */ 0, /* ...args= */ "a", "b")
    some.doNotInvokeMe(/* index = */ 0, /* ...names = */ /*<# …|[javaParameters.dependency.java:136]args| = #>*/"a", "b")

    some.singleParamDslWithSameParamName("no hint param name equals method name")
    some.sameFirstParamNameAndVararg(/*<# [javaParameters.dependency.java:274]name| = #>*/"no hint param name equals method name")
    some.sameFirstParamNameAndVararg(/*<# [javaParameters.dependency.java:274]name| = #>*/"no hint param name equals method name", /*<# …|[javaParameters.dependency.java:290]variables| = #>*/123)
}