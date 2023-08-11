@file:Suppress("UNUSED_PARAMETER")

package sample

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by B [common-2] (sample) Case_2_3 (sample) Press ... to navigate'")!>A_Common<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>common_1_A<!>()
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>common_2_A<!>()
}

actual typealias <!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER, LINE_MARKER("descr='Has expects in common-1 module'")!>A<!> = A_Common

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER, ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common-1 module'"), LINE_MARKER("descr='Is implemented by Case_2_3 (sample) Press ... to navigate'")!>B<!><!> : A {
    actual fun <!LINE_MARKER("descr='Has expects in common-1 module'")!>common_1_B<!>()
    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>common_1_2_B<!>()
}

fun takeOutA_common_2(t: Out<A>) {}
fun takeOutB_common_2(t: Out<B>) {}
fun takeOutA_Common_common_2(t: Out<A_Common>) {}

fun getOutA(): Out<A> = null!!
fun getOutB(): Out<B> = null!!
fun getOutA_Common(): Out<A_Common> = null!!

fun test_case_2(x: B) {
    x.common_1_A()
    x.common_1_B()
    x.common_2_A()
    x.common_1_2_B()
}

fun test_B() {
    val x = getB()
    x.common_1_A()
    x.common_1_B()
    x.common_2_A()
    x.common_1_2_B()
}
