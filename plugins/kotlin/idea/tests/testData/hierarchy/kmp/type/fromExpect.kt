// PLATFORM: Common
// FILE: A.kt
// MAIN
expect open class BaseClass
open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()
abstract class CommonIntermediate : BaseClass()

// PLATFORM: Jvm
// FILE: A.kt
actual open class BaseClass actual constructor()
open class JvmOnlyChild : BaseClass()
class JvmIntermediate : CommonIntermediate()

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass actual constructor()
open class JsOnlyChild : BaseClass()
class JsIntermediate : CommonIntermediate()
