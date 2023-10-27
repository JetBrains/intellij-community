package foo

expect class <!LINE_MARKER("descr='Has actuals in jvmAndJs module'")!>ExpectInCommonActualInMiddle<!>
expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>ExpectInCommonActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT, NO_ACTUAL_FOR_EXPECT!>ExpectInCommonWithoutActual<!>
