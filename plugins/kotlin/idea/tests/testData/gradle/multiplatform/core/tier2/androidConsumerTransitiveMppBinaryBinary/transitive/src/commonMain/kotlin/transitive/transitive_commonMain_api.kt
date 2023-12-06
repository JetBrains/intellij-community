//region Test configuration
// - hidden: line markers
//endregion
package transitive

expect class CommonMainExpect {
    fun commonApi()
}

fun transitiveProduceCommonMainExpect(): CommonMainExpect = null!!
fun transitiveConsumeCommonMainExpect(e: CommonMainExpect) { }
