import static <error descr="Cannot resolve symbol 'foo'">foo</error>.bar.Goo.abs;

class Simple {

  void f0o() {
    <error descr="Cannot resolve method 'abs(double)'">abs</error>(1.0);
  }
}