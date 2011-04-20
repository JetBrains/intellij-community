class Abc3 {
    Abc3 foo(){}
    def <T, S> getAt(T t) {
        def ab= foo()
        throw ab[2]
    }
}

def aa = new Abc3().getAt()
print a<ref>a