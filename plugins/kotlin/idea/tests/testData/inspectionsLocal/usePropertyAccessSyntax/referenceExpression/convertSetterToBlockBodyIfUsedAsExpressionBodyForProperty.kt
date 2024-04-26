// FIX: Use property access syntax
// WITH_STDLIB
var Thread.otherName: String
    get() = getName()
    set(value) = setName<caret>(value)
