package sample

import kotlin.test.Test
import kotlin.test.assertTrue

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_EXPERIMENTAL_WARNING] The expect/actual classes (including interfaces, objects, annotations, enums, actual typealiases) are an experimental feature. You can use -Xexpect-actual-classes flag to suppress this warning." textAttributesKey="WARNING_ATTRIBUTES">expect</warning> class <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests\" :cleanJvmTest :jvmTest --tests \"sample.SampleTests\" --continue"><lineMarker descr="Has actuals in [mppLibrary.jsTest, mppLibrary.jvmTest] modules">SampleTests</lineMarker></lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests.testMe\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests.testMe\" :cleanJvmTest :jvmTest --tests \"sample.SampleTests.testMe\" --continue"><lineMarker descr="Has actuals in [mppLibrary.jsTest, mppLibrary.jvmTest] modules">testMe</lineMarker></lineMarker>()
}
