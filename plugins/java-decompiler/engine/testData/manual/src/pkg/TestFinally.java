package pkg;


import java.util.List;

public class TestFinally {

  public class A<B>{

  }
  public void test(List<A<String>> a) {
    try {
      testThrow();

    }catch (Exception e) {
      e.printStackTrace();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    finally {
      for (A<String> s : a) {
        String a2 = s.toString();
        System.out.println(a2);
      }
    }
  }

  public void testThrow() {

  }
}