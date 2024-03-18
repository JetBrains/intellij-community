package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>ExpectInCommonActualInMiddle<!>

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] modules'; targets=[(text=jvm); (text=js)]")!>ExpectInMiddleActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT, NO_ACTUAL_FOR_EXPECT!>ExpectInMiddleWithoutActual<!>
