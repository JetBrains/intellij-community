class X {
  def foo() {
    def ab = 4

    each {
      each {
        ab = 2

      }
    }

    print ab
  }
}