package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>ExpectInCommonActualInMiddle<!>

expect class <!LINE_MARKER("descr='Has actuals in [jvm, js] module'")!>ExpectInMiddleActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT, NO_ACTUAL_FOR_EXPECT!>ExpectInMiddleWithoutActual<!>
