// "Add default constructor to 'expect' class" "false"
// ENABLE_MULTIPLATFORM
// ACTION: Create subclass
// ACTION: Introduce import alias
// ACTION: Remove constructor call
// ERROR: This class does not have a constructor
// K2_AFTER_ERROR: 'public abstract actual interface A : Any' has no corresponding expected declaration
// K2_AFTER_ERROR: A: expect and corresponding actual are declared in the same module.
// K2_AFTER_ERROR: A: expect and corresponding actual are declared in the same module.
// K2_AFTER_ERROR: This type does not have a constructor.

expect interface A

open class C : A<caret>()

actual interface A