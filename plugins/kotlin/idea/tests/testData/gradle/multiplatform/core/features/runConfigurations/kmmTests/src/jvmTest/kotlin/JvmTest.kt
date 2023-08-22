class JvmTest {
    @kotlin.test.Test
    fun `test in jvmTest - success`() {
        println("Running jvm test: Should be successful!")
    }

    @kotlin.test.Test
    fun `test in jvmTest - failure`() {
        println("Running jvm test: Should fail!")
        kotlin.test.assertEquals(1, 2)
    }
}