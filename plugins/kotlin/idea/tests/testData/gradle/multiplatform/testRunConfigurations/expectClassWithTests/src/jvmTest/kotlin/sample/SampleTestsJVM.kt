package sample

import kotlin.test.Test
import kotlin.test.assertTrue

// JVM
<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. You can use -Xexpect-actual-classes flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573" textAttributesKey="WARNING_ATTRIBUTES">actual</warning> class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.SampleTests\""><lineMarker descr="Has expects in mppLibrary.commonTest module">SampleTests</lineMarker></lineMarker> {
    @Test
    actual fun <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.SampleTests.testMe\""><lineMarker descr="Has expects in mppLibrary.commonTest module">testMe</lineMarker></lineMarker>() {
    }
}
