package transitiveStory.apiJvm.test.smokeTest

import org.junit.Test
import transitiveStory.apiJvm.beginning.tlAPIval
import kotlin.test.assertEquals

class KClassForTheSmokeTestFromApi {
}

class <!LINE_MARKER("descr='Run Test'")!>SomeTestInApiJVM<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>some<!>() {
        println("I'm simple test in `api-jvm` module")
        assertEquals(tlAPIval, 42)
    }

    // KT-33573
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`function with spaces`<!>() {}
}
