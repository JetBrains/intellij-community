class Outer {
    open class B : A() {

    }

    open class C : Y {

    }

    class Inner {
        open class Z : A() {

        }

        open class U : Z() {

        }
    }
}
