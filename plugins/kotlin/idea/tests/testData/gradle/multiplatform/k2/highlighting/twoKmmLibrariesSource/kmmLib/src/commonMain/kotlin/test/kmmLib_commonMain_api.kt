//region Test configuration
// - hidden: line markers
//endregion
package test

expect class CommonMainExpect {
    fun commonApi()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
