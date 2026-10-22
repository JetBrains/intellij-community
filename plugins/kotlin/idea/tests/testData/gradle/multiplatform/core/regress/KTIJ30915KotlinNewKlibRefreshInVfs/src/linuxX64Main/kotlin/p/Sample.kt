//region Test configuration
// - hidden: line markers
//endregion

// Suppress RETURN_VALUE_NOT_USED in some versions
@file:Suppress("warnings")

package p

import kotlinx.cinterop.ExperimentalForeignApi
import myInterop.*

@OptIn(ExperimentalForeignApi::class)
fun test() {
    MyEnum.FOO
    MyEnum.BAR
    MyEnum.BAZ
}
