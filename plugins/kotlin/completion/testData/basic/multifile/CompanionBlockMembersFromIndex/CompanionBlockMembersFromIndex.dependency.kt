// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions
package dependency

class C {
    companion {
        fun bar() {}
        val prop = 1
    }
}
