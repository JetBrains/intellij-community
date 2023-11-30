// FIR_COMPARISON
// FIR_IDENTICAL

annotation class A(val k: KClass<*>)

@A(String::<caret>)
class B

// EXIST: class