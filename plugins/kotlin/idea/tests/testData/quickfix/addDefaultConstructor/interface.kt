// "Add default constructor to 'expect' class" "false"
// ACTION: Create subclass
// ACTION: Introduce import alias
// ACTION: Remove constructor call
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: NO_CONSTRUCTOR
// K2_ERROR: NO_CONSTRUCTOR

interface A

open class C : A<caret>()