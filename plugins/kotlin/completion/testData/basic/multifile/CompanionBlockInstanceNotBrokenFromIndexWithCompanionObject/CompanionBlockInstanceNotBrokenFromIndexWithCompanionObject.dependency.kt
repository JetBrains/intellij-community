// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package dependency

import main.C

companion fun C.baz() {}
fun C.regularExt() {}
