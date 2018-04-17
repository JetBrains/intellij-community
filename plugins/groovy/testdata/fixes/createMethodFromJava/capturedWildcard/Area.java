public class Test {
  private List<? extends CharSequence> sequences = null;

  {
    new A().f<caret>oo(sequences.map());
  }

  interface List<T>  {
    List<? super T> map();
  }
}