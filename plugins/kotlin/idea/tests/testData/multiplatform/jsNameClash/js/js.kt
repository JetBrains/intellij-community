package sample

actual interface <!LINE_MARKER("descr='Has expects in common module'")!>Input<!>

class JSInput : AbstractInput()

// ------------------------------------

expect <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class <!LINE_MARKER("descr='Has actuals in js module'")!>ExpectInJsActualInJs<!><!>
actual <!EXPECT_AND_ACTUAL_IN_THE_SAME_MODULE!>class <!LINE_MARKER("descr='Has expects in js module'")!>ExpectInJsActualInJs<!><!>
