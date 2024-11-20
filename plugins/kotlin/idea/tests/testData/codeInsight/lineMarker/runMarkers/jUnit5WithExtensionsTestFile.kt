// CONFIGURE_LIBRARY: JUnit5
// CONFIGURE_LIBRARY: JUnit5-Pioneer

import org.junitpioneer.jupiter.RetryingTest;
import org.junit.jupiter.api.Test

class <lineMarker descr="Run Test">ClassWithRetryingTestManyTests</lineMarker> {
    @RetryingTest(value = 2)
    fun <lineMarker descr="Run Test">retryingTest</lineMarker>() = Unit

    @Test
    fun <lineMarker descr="Run Test">regularTest</lineMarker>() = Unit
}


class <lineMarker descr="Run Test">ClassWithRetryingTestSingleTest</lineMarker> {
    @RetryingTest(value = 2)
    fun <lineMarker descr="Run Test">retryingTest</lineMarker>() = Unit
}