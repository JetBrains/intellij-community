package sample

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common module'")!>A<!><!><T : A<T>> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>foo<!>(): T
    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>bar<!>() : T
}

fun test_1(a: A<*>) {
    a.foo()
    a.bar()
    a.foo().foo()
    a.bar().bar()
    a.foo().bar()
    a.bar().foo()
}

fun test_2(b: B) {
    b.foo()
    b.bar()
    b.foo().foo()
    b.bar().bar()
    b.foo().bar()
    b.bar().foo()
}
