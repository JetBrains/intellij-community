import javax.swing.*;
class Test {
  String c = null;

  public void main(String[] args){
    p(new I() {
        @Override
        public void f(String s) {
            c.substring(0).toString();
        }
    });
  }

  void p(I i) {}

  interface I {
    void f(String s);
  } 
}