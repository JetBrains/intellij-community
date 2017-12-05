class NotInBlock {

  public void test(boolean b) {
    if(b) //end line comment
      f<caret>or(int i=0;//before condition
      i</*comment in condition*/10;//before update
      i/*comment inside update*/++) {
        //before statement
        System.out.println("Hello!");//in body
      }
  }
}