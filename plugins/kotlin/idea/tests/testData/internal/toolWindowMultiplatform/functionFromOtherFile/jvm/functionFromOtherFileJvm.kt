package functionFromOtherFile

actual fun myFun(x: Int): Boolean = myDependency(x)
