import org.junit.Test

class IntegrationTest {

    @Test
    fun usePublicApi() {
        println(MainPublic().publicApi)
    }

    @Test
    fun useInternalApi() {
        println(MainInternal().internalApi)
    }
}