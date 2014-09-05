public class Argument  {

  void m(A a) {}
  void n() {
    m(new A<caret>() {{ setI(1); setJ(2); }});
  }

  class A {
    void setI(int i) {}
    void setJ(int j) {}
  }
}