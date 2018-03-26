class Bar {
    final f

    def Bar() {
      super()
        f = "bar"
    }

    def Bar(int i) {
        this()
    }

    def Bar(int x, int y) {
        x = 5
        f = "bar"
    }

    def foo() {
        print f
    }
}