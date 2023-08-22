package xxx

import xxx.E1.AAA1
import xxx.E2.*
import xxx.E3.AAA3
import xxx.E4.*

enum class E1 {
    AAA1, BBB1
}

enum class E2 {
    AAA2, BBB2
}

enum class E3 {
    AAA3, BBB3
}

enum class E4 {
    AAA4, BBB4
}

fun f() {
    print(AAA1)
    print(AAA2)
}

enum class E5 {
    AAA4, BBB4
}

enum class E6 {
    AAA4, BBB4
}

enum class E7 {
    AAA4, BBB4;

    enum class E8 {
        AAA4, BBB4;
    }
}

enum class E9 {
    AAA4, BBB4
}

enum class E10 {
    AAA4, BBB4
}

enum class E11 {
    AAA4, BBB4
}

enum class E12 {
    AAA4, BBB4;

    enum class E13 {
        AAA4, BBB4
    }
}

enum class E14 {
    AAA4, BBB4
}