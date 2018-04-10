class X {
  void f(int[] a){
    for(int i: a/*1*/) /*2*/<caret> { //3
      //4
      System.out.println(i/*5*/);//6
      //7
    }//8
    //9
  }
}