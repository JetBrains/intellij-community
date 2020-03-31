class SuperClass {
  def foo(){

  }
}

class ThisClass extends SuperClass{
  class Inner{
    def foo(){
      ThisClass.super.fo<caret>o();
    }
  }

  def foo(){

  }
}