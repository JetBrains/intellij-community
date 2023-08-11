@file:Suppress("UNUSED_PARAMETER")

package sample

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by A [common-2] (sample) B [jvm] (sample) Press ... to navigate'")!>C<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>common_2_C<!>()
}

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER, ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common-1 module'"), LINE_MARKER("descr='Is implemented by B [jvm] (sample) Press ... to navigate'")!>A<!><!> : C {
    actual fun <!LINE_MARKER("descr='Has expects in common-1 module'")!>common_1_A<!>()
    fun <!NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>common_2_A<!>()
}

typealias A_Common_2_Alias = A
typealias B_Common_2_Alias = B
typealias C_Common_2_Alias = C

fun take_A_common_2(func: (A) -> Unit) {}
fun take_B_common_2(func: (B) -> Unit) {}
fun take_C_common_2(func: (C) -> Unit) {}

fun take_A_alias_common_2(func: (A_Common_2_Alias) -> Unit) {}
fun take_B_alias_common_2(func: (B_Common_2_Alias) -> Unit) {}
fun take_C_alias_common_2(func: (C_Common_2_Alias) -> Unit) {}
