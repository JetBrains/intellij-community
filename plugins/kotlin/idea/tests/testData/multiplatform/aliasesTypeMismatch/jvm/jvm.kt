actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>MyCancelException<!> = platform.lib.MyCancellationException

actual open <!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER!>class <!LINE_MARKER("descr='Has expects in common module'")!>OtherException<!><!> : platform.lib.MyIllegalStateException()

fun test() {
    cancel(MyCancelException()) // TYPE_MISMATCH

    other(OtherException())
}
