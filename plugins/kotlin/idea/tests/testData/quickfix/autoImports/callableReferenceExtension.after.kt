import dependency.extensionFun

// "Import extension function 'String.extensionFun'" "true"
// ERROR: Unresolved reference: extensionFun
val v = String::extensionFun<caret>

/* IGNORE_FIR */