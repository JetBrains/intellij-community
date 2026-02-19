// WITH_STDLIB
data class MyWrapper(val value: Boolean)

fun test(nullableBool : MyWrapper?) {
    if (nullableBool != null && nullableBool.value) {
        // ...
    } else {
        // ...
        if (nullableBool?.value == false) {
            // ...
        }
    }
}