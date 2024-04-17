package kotlin.test

@Suppress("ACTUAL_WITHOUT_EXPECT")
@Target(AnnotationTarget.FUNCTION)
public actual annotation class Test

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual inline fun <!LINE_MARKER("descr='Has expects in common module'")!>AssertionErrorWithCause<!>(message: String?, cause: Throwable?): AssertionError =
    AssertionError(message, cause)
