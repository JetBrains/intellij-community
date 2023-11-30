//region Test configuration
// - hidden: line markers
//endregion
class CommonTest {

    @kotlin.test.Test
    fun `test - success`() {
        main()
    }

    @kotlin.test.Test
    fun `test - failure`() {
        kotlin.test.assertNull(main())
    }
}
