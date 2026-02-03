def an = new Anon(3) {
    void run() {
        println foo
    }
}

an.run()

abstract class Anon {
    def foo

    Anon(def foo) {
        this.foo = foo
    }

    abstract void run()
}