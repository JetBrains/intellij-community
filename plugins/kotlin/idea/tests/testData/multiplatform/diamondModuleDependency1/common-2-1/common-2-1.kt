@file:Suppress("UNUSED_PARAMETER")

package sample

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common-1 module'"), LINE_MARKER("descr='Is implemented by B (sample) Press ... to navigate'")!>A<!><!> {
    actual fun <!LINE_MARKER("descr='Has expects in common-1 module'")!>foo<!>()
    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>bar<!>()
}

fun take_A_common_2_1(x: A) {
    x.foo()
    x.bar()
}
