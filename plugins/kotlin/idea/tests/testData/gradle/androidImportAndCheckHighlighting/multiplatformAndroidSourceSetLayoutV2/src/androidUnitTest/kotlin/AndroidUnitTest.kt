import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class <lineMarker descr="Run Test">AndroidUnitTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">someTest</lineMarker>() {
        commonMainExpect()
        CommonMain.invoke()
        AndroidMain.invoke()
        AndroidMain.invoke(ApplicationProvider.getApplicationContext())
        CommonTest().someTest()
    }
}
