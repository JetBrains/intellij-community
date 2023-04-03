//region Test configuration
// - hidden: line markers
//endregion
package transitive

actual class CommonMainExpect {
    actual fun commonApi() { }

    fun jvmApi() { }
}

fun transitiveProduceJvmMainExpect(): CommonMainExpect = null!!
fun transitiveConsumeJvmMainExpect(e: CommonMainExpect) { }
