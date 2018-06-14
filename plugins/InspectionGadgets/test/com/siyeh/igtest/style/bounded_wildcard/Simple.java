package com.siyeh.igtest.abstraction.covariance;

import my.*;
import java.util.function.*;
import java.util.*;

class Simple<T> {
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
  public static <T, V> Supplier<T> map2Set(T collection, Consumer<<warning descr="Can generalize to '? super T'">T</warning>> mapper) {
    String s = map2(mapper, collection);
    return ()-> s == null ? collection : null;
  }
  private static <T> String map2(Consumer<T> mapper, T collection) {
      return null;
  }


  // call with the same name
  public static String getUnhandledExceptionsDescriptor(Collection<<warning descr="Can generalize to '? extends Exception'">Exception</warning>> unhandled) {
    return getUnhandledExceptionsDescriptor(unhandled, null);
  }
  private static String getUnhandledExceptionsDescriptor(Collection<? extends Exception> unhandled, String source) {
    return source == null ? "null" : unhandled.toString();
  }


  ////////////////// complex signature
  interface ProcessorX<T> {
      List<T> process(T t);
  }
  <X> X processX(ProcessorX<X> processorX) {
      return processorX.process(null).get(0);
  }

  //////////////// iteration
  <T> boolean containsNull(List<<warning descr="Can generalize to '? extends T'">T</warning>> list, T oneMore) {
    for (T t : (list)) {
      if (t == null) return true;
    }
    return (list).get(0) == null || oneMore == null;
  }
  /////////////// method ref
  <T> void forEach(Processor<? super T> action, T t) { action.process(t);}
  <P> void lookupMethodTypes0(Processor<P> result, P p) { // useless
    forEach(((result))::process, p);
  }
  <P> void lookupMethodTypes(Processor<<warning descr="Can generalize to '? super P'">P</warning>> result, P t, List<P> p) {
    forEach(((result))::process, t);
    p.add(t);
    result.process(p.get(0));
  }


  ////// both
  public <T> void processZ(Command<T> command) {
    if (command != null) consume(command, command.get());
  }
  interface Command<T> extends Supplier<T>, Consumer<T> {}
  public <T> void consume(Consumer<? super T> consumer, T value) {
    if (consumer != null) consumer.accept(value);
  }


  //////////////// asynch
  private void asynch(AsynchConsumer<<warning descr="Can generalize to '? super String'">String</warning>> asconsumer) {
    asconsumer.accept("changeList");
    asconsumer.finished();
  }
  interface AsynchConsumer<T> extends Consumer<T> {
    void finished();
  }

  ///////////////////overrides generic Function<T>
  void superOVerride() {
    new Function<Consumer<Number>, Number>(){
      @Override
      // makes no sense to insert "? super" here
      public Number apply(Consumer<Number> myconsumer) {
        myconsumer.accept(null);
        return null;
      }
    };
  }
  ////////////// recursive

  // useless
  private static <T> void printNodes0(Function<T, String> getter, T indent) {
    if (indent != null) {
      getter.apply(indent);
      printNodes0(getter, indent);
    }
    else {
      //buffer.append(" [...]\n");
    }
  }

  private static <T> void printNodes(Function<<warning descr="Can generalize to '? super T'">T</warning>, String> getter, T indent, List<T> out) {
    if (indent != null) {
      getter.apply(indent);
      out.add(indent);
      printNodes(getter, indent, out);
      T last = out.get(0);
      assert last != null;
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
  static class Refactor {
    public void substituteElementToRename(String element, Processor<String> renameCallback) {}
  }
  static class RefactorImpl extends Refactor {
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

    boolean foo(S s) {
      return myProcessor.process(s);
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
  boolean castandinstanceof(Processor<<warning descr="Can generalize to '? super Number'">Number</warning>> p, Number n) {
    if (p instanceof Number) return ((Number)p).intValue()==0;
    if ((p) instanceof Number) return ((Number)(p)).intValue()==0;
    return p.process(n);
  }

  //////////// doesn't make sense to replace lone free T with wildcard
  <T> T getT() { return null; }
  <T> boolean lone(Processor<T> p) {
     return p.process(getT());
  }

  // no sense in "Supplier<? extends T>" because it's equally powerful to "Supplier<T>"
  <T> T putProc(Supplier<T> co) {
    return co.get();
  }

  public <T> T[] getExtensions(Processor<T> p) {
    p.process(getT());
    return null;
  }
  private <T> T[] getT(boolean b) { return null; }

  ///// List.addAll has complex signature
  void listAddAll(List<<warning descr="Can generalize to '? super String'">String</warning>> out) {
    out.addAll(new ArrayList<String>());
  }


  /// if co/contravariant parts of the method are not used, skip them
  private static <U> void addToMap(Map<? super Number, <warning descr="Can generalize to '? super U'">U</warning>> map, U value, List<U> out) {
    map.put(1, value);
    out.add(value);
    U last = out.get(0);
    assert last != null;
  }

  // useless
  private static <T> Boolean processImmediatelyIfTooFew0(final List<T> things,
                                                         final Processor<? super T> thingProcessor) {
    for (T thing : things) if (!thingProcessor.process(thing)) return false; return true;
  }
  private static <T> Boolean processImmediatelyIfTooFew1(final List<T> things, int poo,
                                                         final Processor<? super T> thingProcessor) {
    for (T thing : things) if (!thingProcessor.process(thing)) return false; return true;
  }

  interface ArtifactProperties<T> {
    void loadState(T state);
  }
  public static <S> void copyProperties(ArtifactProperties<?> from, ArtifactProperties<S> to) {
    to.loadState((S)from);
  }



}