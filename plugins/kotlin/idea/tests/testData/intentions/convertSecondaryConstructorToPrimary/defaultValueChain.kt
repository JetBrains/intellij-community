class DefaultValueChain {
    val x1: Int
    val x3: Int
    val x5: Int

    <caret>constructor(x1: Int, x2: Int = x1, x3: Int = x2, x4: Int = x3, x5: Int = x4) {
        this.x1 = x1
        this.x3 = x3
        this.x5 = x5
    }
}
