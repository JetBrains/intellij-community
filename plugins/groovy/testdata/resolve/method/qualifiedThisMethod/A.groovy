class OuterClass {
  class Inner{
    def foo() {
      OuterClass.this.fo<caret>o();
    }
  }

  def foo(){

  }
}