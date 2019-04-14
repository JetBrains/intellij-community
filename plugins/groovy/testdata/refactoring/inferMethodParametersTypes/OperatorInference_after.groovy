
class Custom {
  Custom plus(Custom other) {

  }
}

def void f<caret>oo(Custom a) {
  Custom x = new Custom()
  x+a
}

