package pkg;

public class TestNestedInheritor {

  public static class Parent {
    public class NestedParent {

    }
  }

  public static class Child extends Parent {
    public class NestedChild extends Child {
      Integer myInteger;

      public NestedChild(Integer i) {
        myInteger = i;
      }
    }
  }


  public static void main(String[] args) {
  }
}