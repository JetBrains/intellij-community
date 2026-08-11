// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
package p

import p.Toolchain.Companion.DATA_PATH

@RequiresOptIn
annotation class ExperimentalBuildToolsApi

@OptIn(Experimenta<caret>lBuildToolsApi::class)
fun bar() {
  val str = DATA_PATH
}

@ExperimentalBuildToolsApi
public interface Toolchain {
    public companion object {
        public const val DATA_PATH: String = "cri"
    }
}