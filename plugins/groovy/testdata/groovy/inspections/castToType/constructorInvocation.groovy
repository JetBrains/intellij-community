class B{
  B(Integer i){}
}
class C extends B{
  C(){
    super(new <caret>Object())
  }
}
