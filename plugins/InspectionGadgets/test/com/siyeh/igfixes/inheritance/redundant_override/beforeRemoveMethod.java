// "Remove redundant method" "true-preview"
class ParentClass {
  void foo(int x, int y){
    System.out.println(x + y);
  }
}

class ChildForClass extends ParentClass {
  @Override
  void foo<caret>(int x, int y) {
    System.out.println(x + y);
  }
}