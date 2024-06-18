//region Test configuration
// - hidden: line markers
//endregion
package c

import l.MyClass
import l.o.*

fun binaryApple(mc: MyClass) {
    MyClass.V
    mc.mem()

    nonExpectHasHigherPriority(1).<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'nea'.'")!>nea<!>
    expectHasHigherPriority(1).ea
}
