fun f(ac: () -> Unit) {}
fun m() {
    ~f {
        var i = 0
        i
    }
}