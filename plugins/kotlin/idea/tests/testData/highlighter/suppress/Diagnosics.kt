@Suppress("UNUSED_PARAMETER")
fun usedFunction(unusedParameter: Int) = Unit

@Suppress("UNUSED_PARAMETER") // redundant
fun unusedFunction(usedParameter: Int) {
    usedFunction(usedParameter)
}

fun unusedFunctionWithoutAnnotation(<warning descr="[UNUSED_PARAMETER]">unusedParameterWithoutAnnotation</warning>: String) = Unit

// NO_CHECK_INFOS
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
