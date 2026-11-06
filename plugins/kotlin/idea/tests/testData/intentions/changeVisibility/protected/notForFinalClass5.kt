// IS_APPLICABLE: false
enum class EnumClass(val a: Int) {
    ;
    <caret>private constructor() : this(1)
}