class IdeaTest {

  public void test(){
    print(new InnerClass<Integer>().foo(Integer.valueOf(1)));
  }

  public void print(Integer foo){
    System.out.println(foo);
  }

  class Inn<caret>erClass<T>{
    public T foo(T bar){
      return bar;
    }
  }
}