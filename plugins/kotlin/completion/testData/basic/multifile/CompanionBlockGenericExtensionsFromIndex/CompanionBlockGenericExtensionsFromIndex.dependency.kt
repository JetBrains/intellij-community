// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package dependency

import main.C

companion fun <T> C<T>.baz() {}
companion val <T> C<T>.ext: Int get() = 1
