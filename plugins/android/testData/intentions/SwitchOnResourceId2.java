package p1.p2;

public class Test1 {
  public void f(int n) {
    int abacaba = 13;
    
    switch (n) {
      case <error>abac<caret>aba</error>:
        System.out.println("Icon");
        break;
    }
  }
}