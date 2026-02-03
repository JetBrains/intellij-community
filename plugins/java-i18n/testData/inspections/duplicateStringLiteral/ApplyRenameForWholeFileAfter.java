class ClassA {
  public static final String ABC = ClassB.ABC;

  public static void main(String[] args) {
    System.out.println("ABC = " + ABC);
  }
}

class ClassB {
  public static final String ABC = "ABCDEF";
}