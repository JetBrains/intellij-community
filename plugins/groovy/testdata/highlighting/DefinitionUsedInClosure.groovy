class A {
    def r() {
        def a = 0;
        {->
          a.intValue()
        }.call()
    }
}
