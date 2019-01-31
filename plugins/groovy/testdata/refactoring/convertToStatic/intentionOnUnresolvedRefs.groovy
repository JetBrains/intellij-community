import groovy.transform.CompileStatic

@CompileStatic
class Book {
    def title = "LoTR"
    def m() {
        title.toUppe<caret>rCase()
        def (l, m) = l()
    }
    List l(){null}
}
