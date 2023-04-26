//region Test configuration
// - hidden: line markers
//endregion
package test

import direct.*
import transitive.*

fun use() {
    // 1. Transitive dependency works, symbols are visible
    val a = consumerProduceCommonMainExpect()
    a.commonApi()
    a.jvmApi()
    a.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: iosApi'")!>iosApi<!>() // shouldn't be visible

    // 2a Transitive dependency is visible through direct depency, refinement works
    val b = directProduceCommonMainExpect()
    b.commonApi()
    b.jvmApi()
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: iosApi'")!>iosApi<!>() // shouldn't be visible

    // 2a Transitive dependency is visible, refinements works
    val c = transitiveProduceCommonMainExpect()
    b.commonApi()
    b.jvmApi()
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: iosApi'")!>iosApi<!>() // shouldn't be visible

    // 3. Descriptors from 'consumer' equivalent to descriptors from 'transitive'
    val x: CommonMainExpect = transitiveProduceCommonMainExpect()
    transitiveConsumeCommonMainExpect(consumerProduceCommonMainExpect())

    // 4. Descriptors from 'direct' are equivalent to descriptors from 'transitive'
    transitiveConsumeCommonMainExpect(directProduceCommonMainExpect())
    directConsumeCommonMainExpect(transitiveProduceCommonMainExpect())
}
