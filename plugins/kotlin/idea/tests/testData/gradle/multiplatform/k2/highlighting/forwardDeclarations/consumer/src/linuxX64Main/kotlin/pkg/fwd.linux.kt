//region Test configuration
// - hidden: line markers
//endregion
package pkg

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import myInterop.MyEnum
import myInterop.takeStat
import platform.posix.stat
// Forward declaration declared in the project
import cnames.structs.stat as stat2
// Forward declarations from POSIX
import cnames.structs.iface
import cnames.structs.rusage

@OptIn(ExperimentalForeignApi::class)
fun main() {
    MyEnum.FOO

    takeStat(magic() as CValuesRef<stat2>)
}

@OptIn(ExperimentalForeignApi::class)
fun magic(): CValuesRef<stat> = null!!
