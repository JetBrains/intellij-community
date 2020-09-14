import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RegisteredNestedCounterCollector {
  class Foo : CounterUsagesCollector()
}