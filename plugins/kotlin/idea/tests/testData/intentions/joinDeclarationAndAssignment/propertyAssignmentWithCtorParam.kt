class A(param: Any) {
    <caret>val prop: Any

    init {
        prop = param
    }
}