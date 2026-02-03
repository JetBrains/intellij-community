class CommonTest {
    @kotlin.test.Test
    fun `test in commonTest - success`() {

    }

    @kotlin.test.Test
    fun `test in commonTest - failure`() {
        kotlin.test.assertEquals(1, 2)
    }
}