import groovy.transform.CompileStatic

@CompileStatic
class Book {
    static def title(){
        "LoTR"
    }
    def m() {
        title().toUpperCase()
        def (l, m) = l<caret>()
    }
    List l(){null}
}