package kotlin.test

@Target(AnnotationTarget.FUNCTION)
public expect annotation class <!LINE_MARKER("descr='Has actuals in [js, jvm, native] modules'; targets=[(text=js; icon=nodes/ppLibFolder.svg); (text=jvm; icon=nodes/ppLibFolder.svg); (text=native; icon=nodes/ppLibFolder.svg)]")!>Test<!>()

internal expect fun <!LINE_MARKER("descr='Has actuals in [js, jvm, native] modules'; targets=[(text=js; icon=nodes/ppLibFolder.svg); (text=jvm; icon=nodes/ppLibFolder.svg); (text=native; icon=nodes/ppLibFolder.svg)]")!>AssertionErrorWithCause<!>(message: String?, cause: Throwable?): AssertionError
