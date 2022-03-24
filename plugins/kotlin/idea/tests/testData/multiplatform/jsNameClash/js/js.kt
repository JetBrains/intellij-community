package sample

actual interface <!LINE_MARKER("descr='Has expects in common module'")!>Input<!>

class JSInput : AbstractInput()

// ------------------------------------

expect class <!LINE_MARKER("descr='Has actuals in js module'")!>ExpectInJsActualInJs<!>
actual class <!LINE_MARKER("descr='Has expects in js module'")!>ExpectInJsActualInJs<!>
