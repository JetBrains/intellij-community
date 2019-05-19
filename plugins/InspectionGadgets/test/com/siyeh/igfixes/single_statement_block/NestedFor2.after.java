class X {
  void f() {
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) System.out.println(i);<caret>
    }
  }
}