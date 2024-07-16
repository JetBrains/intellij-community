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
// The unresolved reference can be caused by problems with catching VFS updates for freshly generated cinterop libraries, see KTIJ-30124.
// Though it's strange that macOS target is not affected, can be something else, needs investigation.
import cnames.structs.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_IMPORT] Unresolved reference 'stat'.'")!>stat<!> as stat2
// Forward declarations from POSIX
import cnames.structs.iface
import cnames.structs.rusage

@OptIn(ExperimentalForeignApi::class)
fun main() {
    MyEnum.FOO

    // KTIJ-30124
    takeStat(magic() as CValuesRef<<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'stat2'.'")!>stat2<!>>)
}

@OptIn(ExperimentalForeignApi::class)
fun magic(): CValuesRef<stat> = null!!
