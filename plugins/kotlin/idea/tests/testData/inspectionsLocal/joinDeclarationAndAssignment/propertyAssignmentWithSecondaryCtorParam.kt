// PROBLEM: none
class Id {
    val id: Int<caret>

    constructor(id: Int) {
        this.id = id
    }

    constructor() : this(0)
}