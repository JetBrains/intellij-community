package test

interface JsExtraInterface  // platform-specific extra interface

actual interface BaseInterface
actual open class ParentClass : BaseInterface
actual open class BaseClass : ParentClass(), JsExtraInterface  // extra parent

open class JsOnblyChild1 : BaseClass()
open class JsOnblyChild2 : BaseClass()
open class JsOnblyChild3 : BaseClass()