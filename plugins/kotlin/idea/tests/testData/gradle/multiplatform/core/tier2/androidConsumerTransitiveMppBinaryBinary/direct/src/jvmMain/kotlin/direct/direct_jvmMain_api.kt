//region Test configuration
// - hidden: line markers
//endregion
package direct

import transitive.CommonMainExpect

fun directProduceJvmMainExpect(): CommonMainExpect = null!!
fun directConsumeJvmMainExpect(e: CommonMainExpect) { }
