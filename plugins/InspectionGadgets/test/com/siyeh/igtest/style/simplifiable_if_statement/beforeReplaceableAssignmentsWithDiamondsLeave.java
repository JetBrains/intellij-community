// "Replace 'if else' with '?:'" "INFORMATION"
public class TestCompletion {

  public static <T, V> ParallelPipeline<T, V> test(T base, V newStage, T upstream) {
    <caret>if (base != null) {
      return new ParallelPipeline<>(base, newStage);
    }
    else {
      return new ParallelPipeline<>(upstream, newStage);
    }
  }

  private static class ParallelPipeline<T, V> {
    public ParallelPipeline(T p0, V p1) {
    }
  }
}

