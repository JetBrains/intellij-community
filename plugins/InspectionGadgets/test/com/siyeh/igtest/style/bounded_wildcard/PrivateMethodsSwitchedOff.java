package com.siyeh.igtest.abstraction.covariance;

import my.*;
import java.util.function.*;
import java.util.*;

public class PrivateMethodsSwitchedOff<T> {
  private boolean process(Processor<T> processor) {
    return processor.process(null);
  }
  private Number foo2(Function<?, Number> f) {
    return ((f)).apply(null);
  }

  ////////////// inferred from method call
  public static <T, V> String map2Set(T[] collection, Function<? super T, <warning descr="Can generalize to '? extends V'">V</warning>> mapper, V v) {
      return map2((mapper));
  }
  private static <T, V> String map2(Function<? super T, ? extends V> mapper) {
      return null;
  }

}