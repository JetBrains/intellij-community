// "Replace method with delegate to super" "true-preview"
interface ParentInterface {
  default void foo(int x, int y){
    System.out.println(x + y);
  }
}

class ChildForInterface implements ParentInterface {
  public void foo<caret>(int x, int y) {
    System.out.println(x + y);
  }
}