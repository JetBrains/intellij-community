package com.siyeh.igtest.abstraction.covariance;

import my.*;
import java.util.function.*;
import java.util.*;

public class PrivateMethodsSwitchedOff<T> {
  private boolean process(Processor<T> processor, T t) {
    return processor.process(t);
  }
  private Number foo2(Function<Number, Number> f) {
    return ((f)).apply(1);
  }

  ////////////// inferred from method call
  public static <T, V> Supplier<T> map2Set(T collection, Consumer<<warning descr="Can generalize to '? super T'">T</warning>> mapper) {
    String s = map2(mapper, collection);
    return ()-> s == null ? collection : null;
  }
  private static <T> String map2(Consumer<T> mapper, T collection) {
      return null;
  }
}