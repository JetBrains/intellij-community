import kotlin.contracts.contract

fun foo() {
    contract {
        req<caret>
    }
}

// OUT_OF_BLOCK: true
