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
    a.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmApi'")!>jvmApi<!>() // shouldn't be visible
    a.iosApi()

    // 2a Transitive dependency is visible through direct depency, refinement works
    val b = directProduceCommonMainExpect()
    b.commonApi()
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmApi'")!>jvmApi<!>() // shouldn't be visible
    b.iosApi()

    // 2a Transitive dependency is visible, refinements works
    val c = transitiveProduceCommonMainExpect()
    b.commonApi()
    b.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmApi'")!>jvmApi<!>() // shouldn't be visible
    b.iosApi()

    // 3. Descriptors from 'consumer' equivalent to descriptors from 'transitive'
    val x: CommonMainExpect = transitiveProduceCommonMainExpect()
    transitiveConsumeCommonMainExpect(consumerProduceCommonMainExpect())

    // 4. Descriptors from 'direct' are equivalent to descriptors from 'transitive'
    transitiveConsumeCommonMainExpect(directProduceCommonMainExpect())
    directConsumeCommonMainExpect(transitiveProduceCommonMainExpect())
}
