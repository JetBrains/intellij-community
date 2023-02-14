import dummy.createDummyStruct
import dummy.createOtherStruct
import kotlinx.cinterop.pointed
import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
class LinuxTest {
    @Test
    fun dummyTest() {
        useDummy()
        createDummyStruct()?.pointed?.reset()
        assertEquals(0.0, createOtherStruct().useContents { sum(this) })
    }
}
