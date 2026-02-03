class IosTest {
    @kotlin.test.Test
    fun `test in iosTest - success`() {
        println("Running iOS test: Should be successful!")
    }

    @kotlin.test.Test
    fun `test in iosTest - failure`() {
        println("Running iOS test: Should fail!")
        kotlin.test.assertEquals(1, 2)
    }
}