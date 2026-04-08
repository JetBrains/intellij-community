open class Base

@Deprecated("Use Derived instead")
open class LeftDerived : Base()

@Deprecated("Use Derived instead")
open class RightDerived : Base()

open class Derived : Base()

open class SomeNewDerived : Base()


val element: Base = <caret>

// WITH_ORDER
// EXIST: Base
// EXIST: Derived
// EXIST: SomeNewDerived
// EXIST: { "lookupString": "object", "itemText": "object : Base(){...}" }
// EXIST: { "lookupString": "object", "itemText": "object : Derived(){...}" }
// EXIST: { "lookupString": "object", "itemText": "object : SomeNewDerived(){...}" }
// EXIST: LeftDerived
// EXIST: RightDerived
// EXIST: { "lookupString": "object", "itemText": "object : LeftDerived(){...}" }
// EXIST: { "lookupString": "object", "itemText": "object : RightDerived(){...}" }
// IGNORE_K1
