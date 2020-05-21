import java.util.function.Consumer;

class X {
    @interface AAA {}
  
    Consumer<Integer> c1 = (@AAA Integer<caret> i) -> System.out.println(i);
}