// PROBLEM: none
class Id {
    val id: Int<caret>

    constructor(id: Int) {
        this.id = id + 1
    }

    constructor() : this(0)
}