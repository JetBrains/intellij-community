def foo = 2
def an = new Runnable() {
    void run() {
        println foo
    }
}

an.run()

println foo