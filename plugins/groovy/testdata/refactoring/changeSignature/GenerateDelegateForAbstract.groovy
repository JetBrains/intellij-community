abstract class X {
  abstract def foo<caret>(String s) 
}

class Y extends X {
  def foo(String s) {

  }
}
