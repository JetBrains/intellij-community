// WITH_STDLIB
// INTENTION_TEXT: "Add '@JvmOverloads' annotation to primary constructor"
// AFTER-WARNING: Parameter 'b' is never used

class A(val a: String = ""<caret>, b: Int = 0)