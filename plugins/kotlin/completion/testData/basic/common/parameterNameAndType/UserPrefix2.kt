// FIR_COMPARISON
// FIR_IDENTICAL
fun f(myF<caret>)

// EXIST_JAVA_ONLY: { lookupString: "myFile: File", itemText: "myFile: File", tailText: " (java.io)" }
