@file:Suppress("ACTUAL_WITHOUT_EXPECT")

package foo

interface <!LINE_MARKER("descr='Is implemented by A [jvm] (foo) AImpl (foo) Press ... to navigate'")!>B<!>

<!ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER, ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_SUPERTYPES_AS_NON_FINAL_EXPECT_CLASSIFIER!>actual interface <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is implemented by AImpl (foo) Press ... to navigate'")!>A<!><!> : B {
    actual fun <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is implemented in AImpl (foo) Press ... to navigate'")!>commonFun<!>()

    fun <!LINE_MARKER("descr='Is implemented in AImpl (foo) Press ... to navigate'"), NON_ACTUAL_MEMBER_DECLARED_IN_EXPECT_NON_FINAL_CLASSIFIER_ACTUALIZATION!>platformFun<!>()
}

class AImpl : A {
    override fun <!LINE_MARKER("descr='Implements function in A (foo) Press ... to navigate'")!>commonFun<!>() {}
    override fun <!LINE_MARKER("descr='Implements function in A (foo) Press ... to navigate'")!>platformFun<!>() {}
}

@Suppress("UNUSED_PARAMETER")
fun takeList(inv: List<B>) {}
