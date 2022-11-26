import java.util.List;

class PrimitiveItem {
  void foo(List<? extends Object> it) {
        <caret>for (var i : it) System.out.println(i);
  }
}