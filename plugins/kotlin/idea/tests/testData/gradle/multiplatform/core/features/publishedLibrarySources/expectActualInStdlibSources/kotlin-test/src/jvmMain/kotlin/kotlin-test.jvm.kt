package kotlin.test

public actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Test<!> = org.junit.Test

internal actual fun <!LINE_MARKER("descr='Has expects in common module'")!>AssertionErrorWithCause<!>(message: String?, cause: Throwable?): AssertionError {
    val assertionError = if (message == null) AssertionError() else AssertionError(message)
    assertionError.initCause(cause)
    return assertionError
}
