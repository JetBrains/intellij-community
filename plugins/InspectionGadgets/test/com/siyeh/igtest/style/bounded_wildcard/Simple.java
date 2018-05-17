package com.siyeh.igtest.abstraction.covariance;

import my.*;
import java.util.function.*;
import java.util.*;

public class Simple<T> {
  boolean process(Processor<<warning descr="Can generalize to '? super T'">T</warning>> processor) {
    if (((processor) == null) || ((processor) == (this)) & this!=processor) return false;

    if (processor.hashCode() == 0) return false;
    if (proper((processor))) return true;

    return processor.process(null);
  }

  // oopsie, can't change
  boolean process2(Processor<T> processor) {
    return process(processor);
  }

  // no way to super Object
  boolean process3(Processor<Object> processor) {
    return processor.process(null);
  }
  void iter3(Iterable<Object> iterable) {
    for (Object o : iterable) {
      o.hashCode();
    }
  }

  private boolean proper(Processor<? super T> processor) {
    return processor.process(null);
  }


  // nowhere to extend String
  String foo(Function<?, String> f) {
    return f.apply(null);
  }

  private Number foo2(Function<?, <warning descr="Can generalize to '? extends Number'">Number</warning>> f) {
    return ((f)).apply(null);
  }

  ////////////// inferred from method call
  public static <T, V> String map2Set(T[] collection, V v, Function<? super T, <warning descr="Can generalize to '? extends V'">V</warning>> mapper) {
      return map2((mapper));
  }
  private static <T, V> String map2(Function<? super T, ? extends V> mapper) {
      return null;
  }

  ////////////////// too complex signature, consider invariant
  interface ProcessorX<T> {
      List<T> process(T t);
  }
  <X> X processX(ProcessorX<X> processorX) {
      return processorX.process(null).get(0);
  }

  //////////////// iteration
  <T> boolean containsNull(List<<warning descr="Can generalize to '? extends T'">T</warning>> list) {
    for (T t : (list)) {
      if (t == null) return true;
    }
    return (list).get(0) == null;
  }
  /////////////// method ref
  <T> void forEach(Processor<? super T> action) {}
  <P> void lookupMethodTypes(Processor<<warning descr="Can generalize to '? super P'">P</warning>> result, P p) {
    forEach(((result))::process);
  }


  ////// both
  public <T> void process(Command<T> command) {
    if (command != null) consume(command, command.get());
  }
  interface Command<T> extends Supplier<T>, my.Consumer<T> {}
  public <T> void consume(my.Consumer<? super T> consumer, T value) {
    if (consumer != null) consumer.consume(value);
  }


  //////////////// asynch
  private void asynch(AsynchConsumer<<warning descr="Can generalize to '? super String'">String</warning>> asconsumer) {
    asconsumer.consume("changeList");
    asconsumer.finished();
  }
  interface AsynchConsumer<T> extends my.Consumer<T> {
    void finished();
  }

  ///////////////////overrides generic Function<T>
  void superOVerride() {
    new Function<my.Consumer<Number>, Number>(){
      @Override
      // makes no sense to insert "? super" here
      public Number apply(my.Consumer<Number> myconsumer) {
        myconsumer.consume(null);
        return null;
      }
    };
  }
  ////////////// recursive
  private static <T> void printNodes(Function<<warning descr="Can generalize to '? super T'">T</warning>, String> getter, T indent) {
    if (indent != null) {
      getter.apply(null);
      printNodes(getter, indent);
    }
    else {
      //buffer.append(" [...]\n");
    }
  }
  ///////// recursive 2
  public static boolean isBackpointerReference(Object expression, Processor<<warning descr="Can generalize to '? super Number'">Number</warning>> numberCond) {
    if (expression instanceof Number) {
      final String contents = expression.toString();
      return isBackpointerReference(contents, numberCond);
    }
    return expression instanceof Number && numberCond.process((Number)expression);
  }

  //////////// recursive with super
  class Refactor {
    public void substituteElementToRename(String element, Processor<String> renameCallback) {}
  }
  class RefactorImpl extends Refactor {
    @Override
    public void substituteElementToRename(String element, Processor<<warning descr="Can generalize to '? super String'">String</warning>> renameCallback) {
      renameCallback.process(element);
      super.substituteElementToRename(element, renameCallback);
    }
  }

  ///////// can't deduce anything - too general
  private static String findNonCodeAnnotation(Collection<String> annotationNames, Map<Collection<String>, String> map ) {
    // Map.get(Object)
    return map.get(annotationNames);
  }

  //////////////// complex variance
  public static class Ref<T> {
    private T myValue;
    public Ref(T value) {
      myValue = value;
    }
  }
  public static interface FlyweightCapableTreeStructure<T> {
    int getChildren(T parent, Ref<T[]> into);
  }
  public static void lightTreeToBuffer(final FlyweightCapableTreeStructure<Runnable> tree) {
    Ref<Runnable[]> kids = new Ref<>(null);
    int numKids = tree.getChildren(null, kids);
  }


  ////////////// call to anonymous
  class InplaceButton {
    InplaceButton(Processor<? super Number> p) {
    }
  }
  public void foooo(Processor<<warning descr="Can generalize to '? super Number'">Number</warning>> pass) {
    Object myButton = new InplaceButton(pass) {
      @Override
      public String toString() { return ""; }
    };
  }

  ////////////// field assigned from method
  class S {
    Processor<S> myProcessor;

    public S(Processor<<warning descr="Can generalize to '? super S'">S</warning>> myProcessor) {
      this.myProcessor = myProcessor;
    }

    boolean foo() {
      return myProcessor.process(null);
    }
  }

  ////////////// field assigned from nethod but is used outside - can't fix
  class S2 {
    Processor<S2> myProcessor;

    public S2(Processor<S2> myProcessor) {
      (this.myProcessor) = myProcessor;
    }

    public Processor<S2> getProcessor() {
      myProcessor.process(null);
      return myProcessor;
    }
  }

  ///////// instanceof and cast
  boolean castandinstanceof(Processor<<warning descr="Can generalize to '? super Number'">Number</warning>> p) {
    if (p instanceof Number) return ((Number)p).intValue()==0;
    if ((p) instanceof Number) return ((Number)(p)).intValue()==0;
    return p.process(null);
  }

  //////////// doesn't make sense to replace lone free T with wildcard
  <T> boolean lone(Processor<T> p) {
     return p.process(null);
  }

}