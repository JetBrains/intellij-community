package forJsJvmOnly

actual class <!LINE_MARKER!>SomethinJsJvmaSpecial<!> {
}

expect class <!LINE_MARKER("descr='Has actuals in common'")!>SomethinJsJvmaSpecial<!> {
}

expect class <!LINE_MARKER("descr='Has actuals in common'")!>HavingActualNearby<!>
actual class <!LINE_MARKER!>HavingActualNearby<!>