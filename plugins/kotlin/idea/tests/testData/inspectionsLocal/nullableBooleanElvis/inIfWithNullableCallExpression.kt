fun foo() {
    var a: Int? = null
    fun isOddNumber(n: Int?): Boolean? {
        if (n == null) return null
        if (n < 0) return null
        return n % 2 == 1
    }
    if (isOddNumber(a)?.xor(true) <caret>?: false) {

    }
}