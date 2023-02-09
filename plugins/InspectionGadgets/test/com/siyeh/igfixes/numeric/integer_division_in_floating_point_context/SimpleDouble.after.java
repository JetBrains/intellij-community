class SimpleDouble {

  public void test(Integer i) {
    double x = 1 + (double) i<caret> / 5;
    System.out.println(x);
  }
}