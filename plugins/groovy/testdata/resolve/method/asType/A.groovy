
class C {
    def bar = 5
    def box

    def asType(Class c) {
        if (c == D) {
            return new D()
        }
    }
}

class D {
    def D(C c) {}
}



print(new C() a<ref>s D)