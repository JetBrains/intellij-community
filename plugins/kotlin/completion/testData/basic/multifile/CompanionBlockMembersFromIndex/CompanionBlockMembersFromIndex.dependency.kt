// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
package dependency

class C {
    companion {
        fun bar() {}
        val prop = 1
    }
}
