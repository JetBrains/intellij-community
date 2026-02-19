// PROBLEM: none
class A(_prop: String) {
    val prop: String?<caret> = _prop
        get() = if (false) field else null
}
