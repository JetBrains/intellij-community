// "Create field for parameter 'p1'" "true"


class Test{
    private List<Object> p1

    def <T> void f(List<T> p1){
        this.p1 = p1
    }
}

