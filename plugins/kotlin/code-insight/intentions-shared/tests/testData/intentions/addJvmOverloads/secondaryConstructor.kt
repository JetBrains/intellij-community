// WITH_STDLIB
// INTENTION_TEXT: "Add '@JvmOverloads' annotation to secondary constructor"
// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used

class A {
    constructor(a: String = ""<caret>, b: Int)
}