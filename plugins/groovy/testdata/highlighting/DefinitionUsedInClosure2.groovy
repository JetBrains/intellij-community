class <warning descr="Class A is unused">A</warning> {
    def <warning descr="Method r is unused">r</warning>() {
  def a = 0;
  {->
    a.intValue()
  }.call()
}
}
