class Clazz {
  def foo

  Clazz() {
    println "in parameterless constructor"
  }

  Clazz(String s) {
    println "in one parameter constructor"
  }
}

def z = new <caret>Clazz(foo : "bar")

println z.foo
