// WITH_STDLIB
// PROBLEM: none

class Storage(var v: Int)
private val storage = Storage(0)

var Storage.foo: Int
    get() = v
    set(value) { v = value }

var foo: Int
    get() = with(storage) { foo }
    set(value: Int) { with(storage) { foo<caret> = value } }
