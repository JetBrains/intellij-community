class CastMethod {
  public void foo(Number x) {
    System.out.println((Double) x);

    if (x instanceof Float) {
      System.out.println(<warning descr="Cast '(Double.class).cast((x))' conflicts with surrounding 'instanceof' check">(Double.class).cast((x))</warning>);
    }
  }
}