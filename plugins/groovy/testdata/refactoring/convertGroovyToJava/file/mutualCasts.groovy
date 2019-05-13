class X {
  void foo(List l) {
    if (l instanceof String) {
      if (l instanceof MyList) {
        print l
        String v = l.value
      }
    }
  }
}

class MyList {
  String getValue() { '' }
}
