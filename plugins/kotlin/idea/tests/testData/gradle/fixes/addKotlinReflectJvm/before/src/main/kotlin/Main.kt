// "Add 'kotlin-reflect.jar' to the classpath" "true"
class Main {
    class Foo{}
    fun bar() = Foo::class.me<caret>mbers
}