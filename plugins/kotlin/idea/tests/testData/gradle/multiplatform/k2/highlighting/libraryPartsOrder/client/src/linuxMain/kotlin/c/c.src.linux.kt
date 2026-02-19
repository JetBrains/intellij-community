//region Test configuration
// - hidden: line markers
//endregion
package c

import srcl.MyClass
import srcl.o.*

fun srcLinux(mc: MyClass) {
    MyClass.V
    mc.mem()

    nonExpectHasHigherPriority(1)
    expectHasHigherPriority(1)
}
