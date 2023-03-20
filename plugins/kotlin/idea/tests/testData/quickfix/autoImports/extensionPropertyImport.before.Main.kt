// "Import extension property 'String.someVal'" "true"
// ERROR: Unresolved reference: someVal
package test

fun some() {
    "".<caret>someVal
}

/* IGNORE_FIR */