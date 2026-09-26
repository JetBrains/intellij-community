//region Test configuration
// - hidden: line markers
//endregion
package p

import kotlinx.cinterop.ExperimentalForeignApi
import myInterop.*

@OptIn(ExperimentalForeignApi::class)
fun test() {
    MyEnum.FOO
    MyEnum.BAR
    MyEnum.BAZ
}
