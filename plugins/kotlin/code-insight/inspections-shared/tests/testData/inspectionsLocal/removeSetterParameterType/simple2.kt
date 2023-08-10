class My {
    var y: Int = 1
        set(param: <caret>Int) {
            field = param - 1
        }

    var z: Double = 3.14
        private set

    var w: Boolean = true
        set(param) {
            field = !param
        }
}