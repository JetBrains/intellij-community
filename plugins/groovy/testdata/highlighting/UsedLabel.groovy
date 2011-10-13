oo:
for (s in ['a', 'b']) {
  oo1:
  for (s1 in ['a', 'b']) {
    <warning descr="Label 'oo' is already in use">oo</warning>:
    for (s2 in ['a', 'b']) {
    }
  }
}

def good() {
  bar:
  println "hi"

  bar:
  println "hi"
}

def bad() {
  bar:
  for (i in 1.100) {
    <warning descr="Label 'bar' is already in use">bar</warning>:
    println "hi"
    oo: println "hi"
  }
}