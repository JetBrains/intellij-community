@file:Suppress("UNUSED_PARAMETER")

expect open class <!LINE_MARKER("descr='Has actuals in jvm module'")!>MyCancelException<!> : MyIllegalStateException

fun cancel(cause: MyCancelException) {}

expect open class <!LINE_MARKER("descr='Has actuals in jvm module'")!>OtherException<!> : MyIllegalStateException

fun other(cause: OtherException) {}
