// FIR_IDENTICAL
// FIR_COMPARISON
package kotlin

fun some() {
    kotlin.jvm.<caret>
}

// EXIST: internal
// EXIST_K2: internal.