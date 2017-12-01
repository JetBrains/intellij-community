class NotInBlock {

  public void test(boolean b) {
    if(b) //end line comment
    //before update
    {
        int i=0;//before condition
        while (i</*comment in condition*/10) {
          //before statement
          System.out.println("Hello!");//in body
            i/*comment inside update*/++;
        }
    }
  }
}