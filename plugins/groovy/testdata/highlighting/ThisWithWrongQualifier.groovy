class X{
  def foo(){
    X x=new X();
    <error descr="unknown class 'x'">x</error>.this.foo();
  }
}