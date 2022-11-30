fun testCommonKotlinTypeIsAccessibleThroughJava() {
    JavaClass.commonInstance.<!HIGHLIGHTING("severity='ERROR'; descr='[MISSING_DEPENDENCY_CLASS] Cannot access class 'MppCommon'. Check your module classpath for missing or conflicting dependencies'")!>takeCommonClassAsArg<!>(MppCommon)
    JavaClass.expectActualInstance.t.jvmProp
}
