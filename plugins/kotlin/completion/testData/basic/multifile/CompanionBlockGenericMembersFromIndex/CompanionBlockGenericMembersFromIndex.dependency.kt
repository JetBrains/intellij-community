// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
package dependency

class C<T> {
    companion {
        fun bar() {}
        val prop = 1
    }
}
