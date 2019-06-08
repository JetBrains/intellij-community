class C {
  C(Integer i) {
  }

  C(Boolean i, Double d) {
  }

  C(String s, Object o) {
  }
}

class D extends C {
  D() {
    super(new <caret> Object(), new Object())
  }
}
