// WITH_STDLIB
fun f(v1: List<Int>?, v2: List<Int>?, flag: Boolean) {
    when {
        v1 == null && v2 == null -> Unit
        v1 == null && v2 != null && flag -> {
            v2.size
        }
    }
}