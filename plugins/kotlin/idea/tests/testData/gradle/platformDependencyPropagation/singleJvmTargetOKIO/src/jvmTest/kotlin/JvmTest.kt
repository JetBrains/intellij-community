import okio.Path.Companion.toPath
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class JvmTest {
    @Test
    fun `jvm test`() {
        assertEquals(File("myFile"), "myFile".toPath().toFile())
    }
}