// IS_APPLICABLE: false
data class DataClass(val a: Int) {
    <caret>private constructor() : this(1)
}