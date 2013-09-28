public class A2 {
}

class B2 extends A2 {
  public void <caret>a() {
    b();
  }

  private void b() {
  }
}
