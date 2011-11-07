package p1.p2;

public class Test1 {
  public void f(int n) {
    switch (n) {
      case R<caret>.drawable.icon:
        System.out.println("Icon");
        break;
    }
  }
}