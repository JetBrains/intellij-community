import java.util.List;
public class Test {
  <T> void f(List<? extends T> l)  {
    new A().te<caret>st(l);
  }
}
