import dependency.extensionFun

// "Import extension function 'String.extensionFun'" "true"
// ERROR: Unresolved reference: extensionFun
val v = "a"::extensionFun<selection><caret></selection>
