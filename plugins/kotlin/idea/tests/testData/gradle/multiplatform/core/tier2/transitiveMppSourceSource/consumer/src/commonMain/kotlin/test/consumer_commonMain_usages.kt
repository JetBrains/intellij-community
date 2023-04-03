//region Test configuration
// - hidden: line markers
//endregion
package test

import direct.*
import transitive.*

fun consumerProduceCommonMainExpect(): CommonMainExpect = null!!

fun use() {
    // 1. Transitive dependency works, symbols visible
    val a = consumerProduceCommonMainExpect()
    a.commonApi()
    a.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmApi'")!>jvmApi<!>() // shouldn't be visible
    a.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: iosApi'")!>iosApi<!>() // shouldn't be visible

    // 2. Transitive dependency is visible fine when looked through direct dependency
    val b = directProduceCommonMainExpect()
    b.commonApi()
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmApi'")!>jvmApi<!>() // shouldn't be visible
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: iosApi'")!>iosApi<!>() // shouldn't be visible

    // 3. Descriptors from 'consumer' equivalent to descriptors from 'transitive'
    val x: CommonMainExpect = transitiveProduceCommonMainExpect()
    transitiveConsumeCommonMainExpect(consumerProduceCommonMainExpect())

    // 4. Descriptors from 'direct' are equivalent to descriptors from 'transitive'
    transitiveConsumeCommonMainExpect(directProduceCommonMainExpect())
    directConsumeCommonMainExpect(transitiveProduceCommonMainExpect())
}
