import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class UnregisteredNestedCounterCollector {
  class <warning descr="Statistics collector is not registered in plugin.xml">F<caret>oo</warning> :CounterUsagesCollector()
}