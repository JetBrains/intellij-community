// PROBLEM: none

class Storage(var v: Int)
private val storage = Storage(0)

var Storage.foo: Int
    get() = v
    set(value) { v = value }

var foo: Int
    get() = storage.foo
    set(value: Int) { storage.foo<caret> = value }
