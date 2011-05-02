boolean foo() {
  def a = 5

  a.times {
    if (it==2) return 5
  }

}