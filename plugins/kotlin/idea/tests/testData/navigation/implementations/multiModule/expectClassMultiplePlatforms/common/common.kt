package test

expect interface BaseInterface
expect open class ParentClass : BaseInterface
expect open class Base<caret>Class() : ParentClass

open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()

// REF: [testModule_Common] (test).CommonChild1
// REF: [testModule_Common] (test).CommonChild2
// REF: [testModule_JS] (test).BaseClass
// REF: [testModule_JS] (test).JsOnblyChild1
// REF: [testModule_JS] (test).JsOnblyChild2
// REF: [testModule_JS] (test).JsOnblyChild3
// REF: [testModule_JVM] (test).BaseClass
// REF: [testModule_JVM] (test).JvmOnlyChild