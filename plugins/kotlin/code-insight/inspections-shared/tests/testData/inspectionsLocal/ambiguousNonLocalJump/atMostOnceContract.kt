// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
// ERROR: The feature "break continue in inline lambdas" is only available since language version 2.2
// K2_ERROR:
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun foo() {
    while (true) {
        true.ifTrue {
            bre<caret>ak
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean.ifTrue(block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    if (this) block()
}
