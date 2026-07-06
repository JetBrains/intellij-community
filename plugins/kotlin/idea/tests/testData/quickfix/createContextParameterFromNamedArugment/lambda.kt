// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// WITH_STDLIB
// K2_ERROR: NAMED_PARAMETER_NOT_FOUND

fun f1() {}

fun fdemo() {
    f1(callback<caret> = { x: Int -> x.toString() })
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForCalledFunction