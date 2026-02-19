import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class UnregisteredNestedCounterCollector {
  static class <warning descr="Statistics collector is not registered in plugin.xml">F<caret>oo</warning> extends CounterUsagesCollector{}
}