// "Add explicit context arguments" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// K2_ERROR: NO_CONTEXT_ARGUMENT
package app

import lib.Session
import lib.Tx
import lib.commit

fun useE() {
    commit(tx = TODO("Provide Tx") as Tx, session = TODO("Provide Session") as Session)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix