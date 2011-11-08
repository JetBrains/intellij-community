package p1.p2;

public class Test1 {
  public void f(int n) {
    switch (n) {
      case <error>R.dr<caret>awable.icon</error>:
        System.out.println("Icon");
        break;
    }
  }
}