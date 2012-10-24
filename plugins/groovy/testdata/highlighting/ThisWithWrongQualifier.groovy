class X{
  def foo(){
    X x=new X();
    <error descr="Qualified this is allowed only in nested/inner classes">x.this</error>.foo();
  }
}