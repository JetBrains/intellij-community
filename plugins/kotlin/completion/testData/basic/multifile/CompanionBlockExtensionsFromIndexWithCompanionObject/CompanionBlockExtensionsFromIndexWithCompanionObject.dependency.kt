// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package dependency

import main.C

companion fun C.baz() {}
companion val C.ext: Int get() = 1
