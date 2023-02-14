package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>ExpectInCommonActualInPlatforms<!>

actual class <!LINE_MARKER("descr='Has expects in jvmAndJs module'")!>ExpectInMiddleActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT!>ExpectInJsWithoutActual<!>

expect class <!LINE_MARKER("descr='Has actuals in js module'")!>ExpectInJsActualInJs<!>
actual class <!LINE_MARKER("descr='Has expects in js module'")!>ExpectInJsActualInJs<!>
