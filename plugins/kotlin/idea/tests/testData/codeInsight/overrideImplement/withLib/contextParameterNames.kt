// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
package test

import dependency.AChecker

object CheckerImpl : AChecker {
    <caret>
}

// MEMBER: "context(checkerContext: CheckerContext, diagnosticReporter: DiagnosticReporter)\n check(d: String): Unit"
