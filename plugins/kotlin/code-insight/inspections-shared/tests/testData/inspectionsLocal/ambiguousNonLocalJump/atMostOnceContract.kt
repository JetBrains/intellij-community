// WITH_STDLIB
// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+BreakContinueInInlineLambdas
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
