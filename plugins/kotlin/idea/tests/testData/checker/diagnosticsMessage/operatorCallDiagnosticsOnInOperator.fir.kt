fun call() {
    val list : String? = "sdfknsdkfm"
    "x" <error descr="[UNSAFE_OPERATOR_CALL]">in</error> list

    "x" <error descr="[UNSAFE_OPERATOR_CALL]">!in</error> list
}

operator fun CharSequence.contains(other: CharSequence, ignoreCase: Boolean = false): Boolean = true
