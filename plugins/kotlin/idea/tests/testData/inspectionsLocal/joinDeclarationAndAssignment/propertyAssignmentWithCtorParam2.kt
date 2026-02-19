class A(prop: Any) {
    <caret>val prop: Any

    init {
        this.prop = prop
    }
}