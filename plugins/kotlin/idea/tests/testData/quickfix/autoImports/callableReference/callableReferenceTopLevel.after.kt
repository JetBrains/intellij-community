import dependency.topLevelFun

// "Import function 'topLevelFun'" "true"
// ERROR: Unresolved reference: topLevelFun
val v = ::topLevelFun<selection><caret></selection>
