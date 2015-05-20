// "Create field for parameter 'p1'" "true"

class Test{
    private String p1

    def <T extends String> void f(T p1){
        this.p1 = p1
    }
}

