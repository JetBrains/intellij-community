package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>ExpectInCommonActualInPlatforms<!>
actual class <!LINE_MARKER("descr='Has expects in jvmAndJs module'")!>ExpectInMiddleActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT!>ExpectInJvmWithoutActual<!>

expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>ExpectInJvmActualInJvm<!>
actual class <!LINE_MARKER("descr='Has expects in jvm module'")!>ExpectInJvmActualInJvm<!>
