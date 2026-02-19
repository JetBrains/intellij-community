package a

enum class TestEnum {
    VALUE;
}

operator fun TestEnum.plus(other: TestEnum): TestEnum = other

fun f(v: TestEnum): TestEnum = f

<selection>fun g() {
    f(TestEnum.VALUE + TestEnum.VALUE)
}</selection>