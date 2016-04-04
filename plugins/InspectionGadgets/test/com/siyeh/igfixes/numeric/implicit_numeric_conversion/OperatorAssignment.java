class OperatorAssignment {
    public static void main(String[] args) {
      int a = 10;
      double b = 0.5;

      a *= <caret>b;

      System.out.println(a);
    }
}