import dependency.extensionFun

// "Import" "true"
// ERROR: Unresolved reference: extensionFun
val v = String::extensionFun<caret>
