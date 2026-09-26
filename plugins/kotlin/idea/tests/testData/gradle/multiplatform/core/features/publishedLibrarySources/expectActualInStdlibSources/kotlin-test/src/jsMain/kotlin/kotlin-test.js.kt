package kotlin.test

@Target(AnnotationTarget.FUNCTION)
public actual annotation class <!LINE_MARKER("descr='Has expects in common module'")!>Test<!>

internal actual inline fun <!LINE_MARKER("descr='Has expects in common module'")!>AssertionErrorWithCause<!>(message: String?, cause: Throwable?): AssertionError =
    AssertionError(message, cause)
