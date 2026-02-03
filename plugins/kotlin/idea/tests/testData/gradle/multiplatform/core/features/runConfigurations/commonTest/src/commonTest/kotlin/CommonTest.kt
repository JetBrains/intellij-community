package foo.bar

class CommonTest {
    @kotlin.test.Test
    fun success() {

    }

    @kotlin.test.Test
    fun failure() {
        kotlin.test.assertEquals(1, 2)
    }
}