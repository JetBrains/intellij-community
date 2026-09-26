//region Test configuration
// - hidden: line markers
//endregion
package test

actual class CommonMainExpect {
    actual fun commonApi() { }

    fun jvmApi() { }
}

fun produceJvmMainExpect(): CommonMainExpect = null!!
fun consumeJvmMainExpect(e: CommonMainExpect) { }
