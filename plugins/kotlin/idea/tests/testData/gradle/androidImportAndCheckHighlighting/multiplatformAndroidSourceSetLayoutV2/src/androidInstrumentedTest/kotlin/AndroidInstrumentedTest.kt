import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class <lineMarker descr="Run Test">AndroidInstrumentedTest</lineMarker> {

    @Test
    fun <lineMarker descr="Run Test">someTest</lineMarker>() {
        commonMainExpect()
        CommonMain.invoke()
        AndroidMain.invoke()
        AndroidMain.invoke(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    }
}
