package a

import a.TestEnum.*

enum class TestEnum {
    A, B
}
<selection>
val test = "${TestEnum.A} ${TestEnum.B}"
</selection>