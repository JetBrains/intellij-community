import java.util.List;
import java.util.Map;

abstract class NoImports implements List<NoImports.A> {
  Map.Entry entry;
  <warning descr="'A' is unnecessarily qualified with 'NoImports'">NoImports</warning>.A a;

  class A {}
}