class AssignmentToLambdaParameter {

  interface C {
    void consume(Object o);
  }

  static {
    C c = (o) -> {
      System.out.println(o);
      <warning descr="Assignment to lambda parameter 'o'">o</warning> = new Object();
      System.out.println(o);
    };
  }

}