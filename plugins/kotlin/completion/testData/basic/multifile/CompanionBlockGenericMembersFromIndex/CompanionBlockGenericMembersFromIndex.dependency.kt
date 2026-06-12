// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package dependency

class C<T> {
    companion {
        fun bar() {}
        val prop = 1
    }
}
