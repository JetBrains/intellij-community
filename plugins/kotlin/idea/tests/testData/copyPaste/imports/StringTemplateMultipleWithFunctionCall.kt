package a

import a.TestEnum.*

enum class TestEnum {
    A, B
}

fun <T> f(a: T, b: T) {

}

<selection>
val test = "$A ${f<a.TestEnum>(TestEnum.B, B)}"
</selection>