// "Convert to primary constructor" "false"
// IS_APPLICABLE: false

class WithDelegation {
    constructor()

    constructor<caret>(x: Int): this()
}