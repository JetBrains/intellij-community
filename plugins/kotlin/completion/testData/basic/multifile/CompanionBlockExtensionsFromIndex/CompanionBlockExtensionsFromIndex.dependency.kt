// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package dependency

import main.C

companion fun C.baz() {}
companion val C.ext: Int get() = 1
