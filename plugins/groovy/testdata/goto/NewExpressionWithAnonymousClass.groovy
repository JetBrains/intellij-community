abstract class A{
  def A(int x, double y){

  }

  def A(){}

  abstract foo();
}

def a=new A<caret>(3, 5.6){
  def foo(){}
}