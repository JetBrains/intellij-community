// "Create extension function 'Test.invoke'" "false"
// ACTION: Add explicit type arguments
// ACTION: Create function 't'
// ERROR: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: <br>public operator fun <T, R> DeepRecursiveFunction<TypeVariable(T), TypeVariable(R)>.invoke(value: TypeVariable(T)): TypeVariable(R) defined in kotlin

// KTIJ-1262 "Create extension function 'Test.invoke'" quickfix should suggest for variable with lambda argument
class Test

fun test() {
    var t = Test()
    <caret>t{

    }
}