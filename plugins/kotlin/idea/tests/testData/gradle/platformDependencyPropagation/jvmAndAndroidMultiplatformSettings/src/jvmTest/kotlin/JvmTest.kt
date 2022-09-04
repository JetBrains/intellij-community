import com.russhwolf.settings.PropertiesSettings
import org.junit.Test
import java.util.*

class JvmTest {
    @Test
    fun `jvm test`() {
        myExpectation(someSettings)
        myExpectation(PropertiesSettings(Properties()))
    }
}
