class A {
  def r(Serializable i) {
   print "serializable"
  }
}

class B extends A {
  def r(Object o) {
   print "object"
  }
}

new B() .<ref>r("")