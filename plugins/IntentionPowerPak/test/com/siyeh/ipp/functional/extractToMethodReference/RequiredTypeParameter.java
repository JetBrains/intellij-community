interface I<T> {
  void m(T t);
}
class B {
  <N> void n(){
    I<N> i = i1 -> {
      System.out.prin<caret>tln(i1);
      System.out.println(i1);
    }
  }
}