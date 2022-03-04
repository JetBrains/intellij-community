// IS_APPLICABLE: true

fun Int?.orZero(): Int {
    retur<caret>n this
           ?: 0
}