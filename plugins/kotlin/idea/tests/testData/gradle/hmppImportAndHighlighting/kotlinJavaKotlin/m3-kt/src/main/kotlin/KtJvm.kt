fun testCommonKotlinTypeIsAccessibleThroughJava() {
    JavaClass.commonInstance.takeCommonClassAsArg(MppCommon)
    JavaClass.expectActualInstance.t.jvmProp
}
