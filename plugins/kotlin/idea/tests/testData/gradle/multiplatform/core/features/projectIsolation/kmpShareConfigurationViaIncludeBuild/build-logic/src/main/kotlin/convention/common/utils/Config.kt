package convention.common.utils

object Config {
  val optIns: List<String> = listOf(
    "kotlin.RequiresOptIn",
    "kotlin.experimental.ExperimentalTypeInference",
    "kotlin.contracts.ExperimentalContracts"
  )

  val compilerArgs: List<String> = listOf(
    "-Xexpect-actual-classes",
    "-Xconsistent-data-class-copy-visibility",
    "-Xsuppress-warning=NOTHING_TO_INLINE",
    "-Xsuppress-warning=UNUSED_ANONYMOUS_PARAMETER"
  )

  val jvmCompilerArgs: List<String> = buildList {
    addAll(compilerArgs)
    add("-Xjvm-default=all")
    add("-Xstring-concat=inline")
  }
}
