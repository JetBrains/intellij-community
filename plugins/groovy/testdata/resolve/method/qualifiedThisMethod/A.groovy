class OuterClass {
  class Inner{
    def foo() {
      OuterClass.this.fo<ref>o();
    }
  }

  def foo(){

  }
}