package sample

expect fun <!LINE_MARKER("descr='Has actuals in common module'")!>sameFile<!>()

actual fun <!LINE_MARKER("descr='Has expects in common module'")!>sameFile<!>() = Unit

expect fun <!LINE_MARKER("descr='Has actuals in common module'")!>sameModule<!>()

fun noExpectActualDeclaration() = Unit
