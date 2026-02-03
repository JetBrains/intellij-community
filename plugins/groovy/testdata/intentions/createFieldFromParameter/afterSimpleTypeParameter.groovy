// "Create field for parameter 'p1'" "true"

class Test{
    private Object p1

    def <T> void f(T p1){
        this.p1 = p1
    }
}

