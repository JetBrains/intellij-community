enum class MyEnum {
    FIRST,
    SECOND
}
annotation class MyAnnotation(val enums: Array<MyEnum>)
@MyAnnotation(enums = [MyEnum.FIRST, <caret>])
fun method() { }

// EXIST: FIRST
// EXIST: SECOND
// IGNORE_K1
