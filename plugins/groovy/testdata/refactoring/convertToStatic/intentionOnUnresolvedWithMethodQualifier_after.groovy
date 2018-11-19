import groovy.transform.CompileStatic

@CompileStatic
class Book {
    static String title(){
        "LoTR"
    }
    def m() {
        title().toUpperCase()
        def list = l()
        def (l, m) = [list[0], list[1]]
    }
    List l(){null}
}