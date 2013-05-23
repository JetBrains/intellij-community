package org.hanuna.gitalk.ui.tables.refs.refs;

import java.util.Enumeration;
import java.util.Iterator;

/**
 * @author erokhins
 */
public class IterableEnumeration<R, T extends R> implements Iterable<T> {
    private final Enumeration<? extends R> enumeration;

    public IterableEnumeration(Enumeration<? extends R> enumeration) {
        this.enumeration = enumeration;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return enumeration.hasMoreElements();
            }

            @Override
            public T next() {
                return (T) enumeration.nextElement();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
