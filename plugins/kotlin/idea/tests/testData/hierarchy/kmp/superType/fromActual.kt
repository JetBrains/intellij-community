// PLATFORM: Common
// FILE: A.kt

expect open class BaseClass: ParentClass()
open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()
abstract class CommonIntermediate : BaseClass()
expect open class ParentClass

// PLATFORM: Jvm
// FILE: A.kt
// MAIN
actual open class BaseClass actual constructor(): ParentClass()
open class JvmOnlyChild : BaseClass()
class JvmIntermediate : CommonIntermediate()
actual open class ParentClass actual constructor()

// PLATFORM: Js
// FILE: A.kt
actual open class BaseClass actual constructor(): ParentClass()
open class JsOnlyChild : BaseClass()
class JsIntermediate : CommonIntermediate()
actual open class ParentClass actual constructor()