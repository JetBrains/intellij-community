open class Base {
    open var foo: String = ""
        protected set
}

class Bar : Base() {
    override var foo: String = ""
        public <caret>set(value) {
            field = value
        }
}