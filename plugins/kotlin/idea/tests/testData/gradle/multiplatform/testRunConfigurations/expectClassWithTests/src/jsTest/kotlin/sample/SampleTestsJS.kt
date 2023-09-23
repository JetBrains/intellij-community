package sample

import kotlin.test.Test
import kotlin.test.assertTrue

// JS
<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_EXPERIMENTAL_WARNING] The expect/actual classes (including interfaces, objects, annotations, enums, actual typealiases) are an experimental feature. You can use -Xexpect-actual-classes flag to suppress this warning." textAttributesKey="WARNING_ATTRIBUTES">actual</warning> class <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests\" --continue"><lineMarker descr="Has expects in mppLibrary.commonTest module">SampleTests</lineMarker></lineMarker> {
    @Test
    actual fun <lineMarker descr="Run Test" settings=":cleanJsBrowserTest :jsBrowserTest --tests \"sample.SampleTests.testMe\" :cleanJsNodeTest :jsNodeTest --tests \"sample.SampleTests.testMe\" --continue"><lineMarker descr="Has expects in mppLibrary.commonTest module">testMe</lineMarker></lineMarker>() {
    }
}
