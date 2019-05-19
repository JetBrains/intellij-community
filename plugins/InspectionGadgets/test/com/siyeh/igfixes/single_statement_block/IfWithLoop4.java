class X {
  void f(int[] a) {
    for(int aa : a)
      if(aa <caret>> 0) {
        System.out.println(
          aa
        );
      }
  }
}