interface I1 {
    val foo: Int
}

interface I2 {
    val foo: Int
}

interface I3 : I1, I2

class B : I3 {
    override val f<caret>oo: Int = 5

}