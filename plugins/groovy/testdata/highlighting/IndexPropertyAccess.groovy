class X extends HashMap<Integer, Integer> {
  def getAt(def k) {
    return "string"
  }
}

def x = new X();
print x[2].concat("a")