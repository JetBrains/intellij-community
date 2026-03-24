package test

interface JsExtraInterface  // platform-specific extra interface

actual interface BaseInterface
actual open class ParentClass : BaseInterface
actual open class Base<caret>Class : ParentClass(), JsExtraInterface  // extra parent

open class JsOnblyChild1 : BaseClass()
open class JsOnblyChild2 : BaseClass()
open class JsOnblyChild3 : BaseClass()

// REF: [testModule_JS] (test).JsOnblyChild1
// REF: [testModule_JS] (test).JsOnblyChild2
// REF: [testModule_JS] (test).JsOnblyChild3

// K2_REF: [testModule_Common] (test).CommonChild1
// K2_REF: [testModule_Common] (test).CommonChild2
// K2_REF: [testModule_JS] (test).JsOnblyChild1
// K2_REF: [testModule_JS] (test).JsOnblyChild2
// K2_REF: [testModule_JS] (test).JsOnblyChild3

