package sample

import kotlin.test.Test
import kotlin.test.assertTrue

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573" textAttributesKey="WARNING_ATTRIBUTES">expect</warning> class <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests\" :cleanJvmTest :jvmTest --tests \"sample.SampleTests\" --continue"><lineMarker descr="Has actuals in [mppLibrary.jsTest, mppLibrary.jvmTest] modules">SampleTests</lineMarker></lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests.testMe\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests.testMe\" :cleanJvmTest :jvmTest --tests \"sample.SampleTests.testMe\" --continue"><lineMarker descr="Has actuals in [mppLibrary.jsTest, mppLibrary.jvmTest] modules">testMe</lineMarker></lineMarker>()
}
