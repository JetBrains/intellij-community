// "Create secondary constructor" "true"
// K2_ACTION: "Create constructor in 'J'" "true"
// ERROR: Too many arguments for public/*package*/ constructor J() defined in J

internal class B: J {
    constructor(): super(1) {

    }
}