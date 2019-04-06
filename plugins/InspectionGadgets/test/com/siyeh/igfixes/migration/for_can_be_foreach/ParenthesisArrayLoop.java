class X {

  void x(String[] array) {
    <caret>for/*1*/ (int i = /*2*/(0); (i) < (((/*3*/array).length)); (/*4*/i)++) {
      out.println(((array)/*5*/[(i)]));
    }
  }
}