package com.intellij.internal.statistic.tests;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class RegisteredNestedCounterCollector {
  static class Foo extends CounterUsagesCollector{}
}