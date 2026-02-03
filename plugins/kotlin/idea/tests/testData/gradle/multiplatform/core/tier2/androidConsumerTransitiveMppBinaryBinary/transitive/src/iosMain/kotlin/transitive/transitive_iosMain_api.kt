//region Test configuration
// - hidden: line markers
//endregion
package transitive

actual class CommonMainExpect {
    actual fun commonApi() { }

    fun iosApi() { }
}

fun transitiveProduceIosMainExpect(): CommonMainExpect = null!!
fun transitiveConsumeIosMainExpect(e: CommonMainExpect) { }
