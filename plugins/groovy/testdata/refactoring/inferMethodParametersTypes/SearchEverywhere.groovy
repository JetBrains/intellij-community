def void f<caret>oo(a, b) {
  bar(bar(bar(baz(a))))

  if (1 > 2) {
    quux(b)
  }
}


def <T> void bar(T a) {

}

def baz(Integer a) {

}

def quux(String s) {

}

