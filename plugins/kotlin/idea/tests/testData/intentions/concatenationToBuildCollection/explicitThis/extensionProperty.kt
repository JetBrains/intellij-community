val MutableList<Int>.bbb: Int
    get() {
        this +<caret> this.map { it } + this.size + this.get(1) + this.mapTo(this) { it }
        return 1
    }