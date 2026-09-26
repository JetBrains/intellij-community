package foo

actual class <!LINE_MARKER("descr='Has expects in common module'")!>ExpectInCommonActualInPlatforms<!>

actual class <!LINE_MARKER("descr='Has expects in jvmAndJs module'")!>ExpectInMiddleActualInPlatforms<!>

expect class <!NO_ACTUAL_FOR_EXPECT!>ExpectInJsWithoutActual<!>

expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class <!LINE_MARKER("descr='Has actuals in js module'")!>ExpectInJsActualInJs<!><!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class <!LINE_MARKER("descr='Has expects in js module'")!>ExpectInJsActualInJs<!><!>
