// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// K2_ERROR: [NAMED_PARAMETER_NOT_FOUND] No parameter with name 'ctx' found.
package caller

import target.Ctx
import target.f1

fun fdemo() {
    f1(ctx = Ctx())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CreateContextParameterFix$ForCalledFunction