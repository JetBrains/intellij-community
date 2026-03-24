package test

expect interface BaseInterface
expect open class ParentClass : BaseInterface
expect open class BaseClass() : ParentClass

open class CommonChild1 : BaseClass()
open class CommonChild2 : BaseClass()