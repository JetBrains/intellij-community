// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package dependency

import main.C

companion fun C.baz() {}
fun C.regularExt() {}
