enum class MyEnum {
    FIRST,
    SECOND
}
annotation class MyAnnotation(vararg val enum: MyEnum)
@MyAnnotation(enum = [MyEnum.FIRST, <caret>])
fun method() { }

// EXIST: FIRST
// EXIST: SECOND