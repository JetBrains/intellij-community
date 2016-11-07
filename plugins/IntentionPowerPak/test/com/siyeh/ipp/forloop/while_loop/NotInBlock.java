class NotInBlock {

  public void test(boolean b) {
    if(b)
      for<caret>(int i=0; i<10; i++) {
        System.out.println("Hello!");
      }
  }
}