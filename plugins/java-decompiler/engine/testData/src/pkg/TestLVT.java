package pkg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestLVT {
  public static void method(String a1, String a2) {
    String scope1 = "scope1";
    String scope1a = "scope1a";
    for (int i=0; i<10; i++) {
      String scope2 = "scope2";
      String scope2a = "scope2a";
      List<Object> noise = new ArrayList<Object>();
      String spam = scope1 + scope2 + scope2a + i + noise;
      System.out.println(spam);
    }
    for (long i=0; i<10; i++) {
      String scope2 = "scope2+1";
      String scope2a = "scope2+1a";
      Map<Object,Object> noise = new HashMap<Object,Object>();
      String spam = scope1a + scope2 + scope2a + i + noise;
      System.out.println(spam);
    }
  }

  public void methoda() {
    double a = 0D;
    double b = 1D;
    System.out.println(a+b);
    a = 0.1D;
    b = 1.1D;
    System.out.println(a+b);
  }
}
