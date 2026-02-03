import groovy.transform.CompileStatic

@CompileStatic
class Book {
    String title = "LoTR"
    def m() {
        title.toUppe<caret>rCase()
        def list = l()
        def (l, m) = [list[0], list[1]]
    }
    List l(){null}
}
