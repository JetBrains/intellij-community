// PLATFORM: Common
// FILE: A.kt
// MAIN
open class Base
expect open class BaseClass : Base
open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()
abstract class CommonIntermediate : BaseClass()

// PLATFORM: Jvm
// FILE: A.kt
actual open class BaseClass actual constructor(): Base()
open class JvmOnlyChild : BaseClass()
class JvmIntermediate : CommonIntermediate()

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass actual constructor(): Base()
open class JsOnlyChild : BaseClass()
class JsIntermediate : CommonIntermediate()
