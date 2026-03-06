package test

actual interface BaseInterface
actual open class ParentClass : BaseInterface
actual open class BaseClass() : ParentClass()

open class JvmOnlyChild : BaseClass()
