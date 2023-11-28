//region Test configuration
// - hidden: line markers
//endregion
package test

actual class CommonMainExpect {
    actual fun commonApi() { }

    fun iosApi() { }
}

fun produceIosMainExpect(): CommonMainExpect = null!!
fun consumeIosMainExpect(e: CommonMainExpect) { }
