class NotInBlock {

  public void test(boolean b) {
    if(b) {
        int i=0;
        while (i<10) {
          System.out.println("Hello!");
            i++;
        }
    }
  }
}