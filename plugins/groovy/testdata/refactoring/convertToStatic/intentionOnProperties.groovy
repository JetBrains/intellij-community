import groovy.transform.CompileStatic

@CompileStatic
class Book {
    def author = ""
    def m() {
        author.toUpperCase()
    }

}


@CompileStatic
def m() {
    print new Book().author.toUpperCase()
    int a<caret> = "fd"
}