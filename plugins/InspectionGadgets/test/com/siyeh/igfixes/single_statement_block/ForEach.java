class X {
  void f(int[] a){
    for(int i: a) <caret> {
      System.out.println(i);
    }
  }
}