// "Create field for parameter 'p1'" "true"


class Test{
    private List<String> p1

    def <T extends String> void f(List<T> p1){
        this.p1 = p1
    }
}

