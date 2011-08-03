class Foo{
  void foo() throws IO<caret>Exception {
    throw new FileNotFoundException();
  }
}