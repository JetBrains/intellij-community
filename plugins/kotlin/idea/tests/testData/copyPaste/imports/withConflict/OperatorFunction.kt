package a

import a.TestEnum.VALUE

enum class TestEnum {
    VALUE;
}

operator fun TestEnum.plus(other: TestEnum): TestEnum = other

fun f(v: TestEnum): TestEnum = f

<selection>fun g() {
    f(VALUE + VALUE)
}</selection>