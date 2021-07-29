class Outer {
    open class T : A

    object O1 : A {}

    class Inner {
        object O2 : T() {}
    }
}
