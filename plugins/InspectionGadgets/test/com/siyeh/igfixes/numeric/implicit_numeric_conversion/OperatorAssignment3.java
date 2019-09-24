class OperatorAssignment {
  public static void main(String[] args) {
    byte a = 10;
    byte b = 0.5;

    b<caret> += a;

    System.out.println(b);
  }
}