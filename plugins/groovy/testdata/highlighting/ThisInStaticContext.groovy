class X{
  static foo(){
    <error descr="Cannot reference nonstatic symbol 'this' from static context">this</error>.toString()
  }
}