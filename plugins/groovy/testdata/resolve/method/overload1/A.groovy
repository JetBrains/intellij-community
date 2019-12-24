class P {
  def r(Serializable i) {
   print "serializable"
  }
}

class C extends P {
  def r(Object o) {
   print "object"
  }
}

new C() .<caret>r("")