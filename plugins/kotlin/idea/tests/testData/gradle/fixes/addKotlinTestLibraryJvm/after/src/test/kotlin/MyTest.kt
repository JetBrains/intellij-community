// "Add 'org.jetbrains.kotlin:kotlin-test' library" "true"
// ERROR: Unresolved reference: Test
// K2_ERROR: Unresolved reference 'Test'.
class MyTest {
    @Test
    fun testFoo() {

    }
}
