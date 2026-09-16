class Box(val valueA: Any, val valueZ: Any) {
    fun Box.test(): Number {
        if (this@Box.valueA is String && this@test.valueA is Int) {
            return value<caret>
        }
        return 0
    }
}

// EXIST: { lookupString: "valueA", typeText: "Int" }
