package sample

import kotlin.test.Test
import kotlin.test.assertTrue

// JVM
actual class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.SampleTests\""><lineMarker descr="Has expects in mppLibrary.commonTest module">SampleTests</lineMarker></lineMarker> {
    @Test
    actual fun <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.SampleTests.testMe\""><lineMarker descr="Has expects in mppLibrary.commonTest module">testMe</lineMarker></lineMarker>() {
    }
}
