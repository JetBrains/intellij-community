import okio.Path.Companion.toPath
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class CommonTest {
    @Test
    fun `common test`() {
        assertEquals(File("myFile"), "myFile".toPath().toFile())
    }
}
