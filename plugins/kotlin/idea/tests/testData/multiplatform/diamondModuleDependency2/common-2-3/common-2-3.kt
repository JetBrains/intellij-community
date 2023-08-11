@file:Suppress("UNUSED_PARAMETER")
package sample

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common-1 module'"), LINE_MARKER("descr='Is implemented by D (sample) Press ... to navigate'")!>A<!><!> {
    actual fun <!LINE_MARKER("descr='Has expects in common-1 module'")!>foo_A<!>()
    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>foo_A_3<!>()
}

fun take0(x: A): DD = null!!
fun take1(x: A): DD = null!!
fun take2(x: A): DD = null!!
fun take4(x: A): DD = null!!

fun test(x: A) {
    <!OVERLOAD_RESOLUTION_AMBIGUITY!>take4<!>(x)
}
