//region Test configuration
// - hidden: line markers
//endregion
fun testCommonKotlinTypeIsAccessibleThroughJava() {
    JavaClass.commonInstance.takeCommonClassAsArg(MppCommon)
    JavaClass.expectActualInstance.t.jvmProp
}
