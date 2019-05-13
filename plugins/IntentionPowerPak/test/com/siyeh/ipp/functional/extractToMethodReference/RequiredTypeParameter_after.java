interface I<T> {
  void m(T t);
}
class B {
    private static <N> void m(N i1) {
        System.out.println(i1);
        System.out.println(i1);
    }

    <N> void n(){
    I<N> i = B::m
  }
}