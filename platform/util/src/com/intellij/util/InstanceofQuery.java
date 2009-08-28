package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class InstanceofQuery<T> implements Query<T> {
  private final Class<? extends T>[] myClasses;
  private final Query<?> myDelegate;

  public InstanceofQuery(Query<?> delegate, Class<? extends T>... aClasses) {
    myClasses = aClasses;
    myDelegate = delegate;
  }

  @NotNull
  public Collection<T> findAll() {
    ArrayList<T> result = new ArrayList<T>();
    Collection all = myDelegate.findAll();
    for (Object o : all) {
      for (Class aClass : myClasses) {
        if (aClass.isInstance(o)) {
          result.add((T)o);
        }
      }
    }
    return result;
  }

  public T findFirst() {
    final CommonProcessors.FindFirstProcessor<T> processor = new CommonProcessors.FindFirstProcessor<T>();
    forEach(processor);
    return processor.getFoundValue();
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    return myDelegate.forEach(new Processor() {
      public boolean process(Object o) {
        for (Class aClass : myClasses) {
          if (aClass.isInstance(o)) {
            return consumer.process(((T)o));
          }
        }
        return true;
      }
    });
  }

  public T[] toArray(T[] a) {
    final Collection<T> all = findAll();
    return all.toArray(a);
  }

  public Iterator<T> iterator() {
    return new UnmodifiableIterator<T>(findAll().iterator());
  }
}
