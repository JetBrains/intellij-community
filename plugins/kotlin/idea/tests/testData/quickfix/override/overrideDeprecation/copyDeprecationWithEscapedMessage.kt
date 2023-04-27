// "Copy '@Deprecated' annotation from 'MyInterface.deprecatedFun' to 'MyImplementation.deprecatedFun'" "true"
// WITH_STDLIB

interface MyInterface {
    @Deprecated("A \"deprecated\" message with quotes")
    fun deprecatedFun()
}

class MyImplementation : MyInterface {
    override fun <caret>deprecatedFun() {
    }
}