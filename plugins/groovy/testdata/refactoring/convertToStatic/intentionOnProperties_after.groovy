import groovy.transform.CompileStatic

@CompileStatic
class Book {
    String author = ""
    def m() {
        author.toUpperCase()
    }

}


@CompileStatic
def m() {
    print new Book().author.toUpperCase()
    int a = "fd" as int
}