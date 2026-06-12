// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package dependency

import main.C

companion fun <T> C<T>.baz() {}
companion val <T> C<T>.ext: Int get() = 1
