class B {
    companion object <error>A</error> {
    }

    val <error>A</error> = this
}

class C {
    companion object A {
        val A = this
    }

}
