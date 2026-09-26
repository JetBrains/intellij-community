//region Test configuration
// - hidden: line markers
//endregion
import kotlin.test.Test

class NativeTest {

    @Test
    fun testPublicApi() {
        println(NativeMainPublicApi().publicApi)
    }

    @Test
    fun testInternalApi() {
        println(NativeMainInternalApi().internalApi)
    }

}
