// PLATFORM: Common
// FILE: A.kt

expect open class BaseClass {
    open fun base()
}
open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()
abstract class CommonIntermediate : BaseClass()

// PLATFORM: Jvm
// FILE: A.kt
// MAIN
actual open class BaseClass actual constructor() {
    actual open fun base() {}
}
open class JvmOnlyChild : BaseClass() {
    override fun base() {}
}
class JvmIntermediate : CommonIntermediate()

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass actual constructor() {
     actual open fun base() {}
}
open class JsOnlyChild : BaseClass() {
    override fun base() {}
}
class JsIntermediate : CommonIntermediate()