//region Test configuration
// - hidden: line markers
//endregion
package c

import l.MyClass
import l.o.*

fun binaryLinux(mc: MyClass) {
    MyClass.V
    mc.mem()

    nonExpectHasHigherPriority(1)
    expectHasHigherPriority(1)
}
