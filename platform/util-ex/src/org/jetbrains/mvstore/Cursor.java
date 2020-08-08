/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A cursor to iterate over elements in ascending or descending order.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class Cursor<K,V> implements Iterator<K> {
    private final boolean reverse;
    private final K to;
    private CursorPos<K,V> cursorPos;
    private CursorPos<K,V> keeper;
    private K current;
    private K last;
    private V lastValue;
    private Page<K,V> lastPage;


    public Cursor(RootReference<K,V> rootReference, K from, K to) {
        this(rootReference, from, to, false);
    }

    /**
     * @param rootReference of the tree
     * @param from starting key (inclusive), if null start from the first / last key
     * @param to ending key (inclusive), if null there is no boundary
     * @param reverse true if tree should be iterated in key's descending order
     */
    public Cursor(RootReference<K,V> rootReference, K from, K to, boolean reverse) {
        this.lastPage = rootReference.root;
        this.cursorPos = traverseDown(lastPage, from, reverse);
        this.to = to;
        this.reverse = reverse;
    }

    @Override
    public boolean hasNext() {
        if (cursorPos != null) {
            int increment = reverse ? -1 : 1;
            while (current == null) {
                Page<K,V> page = cursorPos.page;
                int index = cursorPos.index;
                if (reverse ? index < 0 : index >= upperBound(page)) {
                    // traversal of this page is over, going up a level or stop if at the root already
                    CursorPos<K,V> tmp = cursorPos;
                    cursorPos = cursorPos.parent;
                    if (cursorPos == null) {
                        return false;
                    }
                    tmp.parent = keeper;
                    keeper = tmp;
                } else {
                    // traverse down to the leaf taking the leftmost path
                    while (!page.isLeaf()) {
                        page = page.getChildPage(index);
                        index = reverse ? upperBound(page) - 1 : 0;
                        if (keeper == null) {
                            cursorPos = new CursorPos<>(page, index, cursorPos);
                        } else {
                            CursorPos<K,V> tmp = keeper;
                            keeper = keeper.parent;
                            tmp.parent = cursorPos;
                            tmp.page = page;
                            tmp.index = index;
                            cursorPos = tmp;
                        }
                    }
                    if (reverse ? index >= 0 : index < page.getKeyCount()) {
                        K key = page.getKey(index);
                        if (to != null && Integer.signum(page.map.getKeyType().compare(key, to)) == increment) {
                            return false;
                        }
                        current = last = key;
                        lastValue = page.getValue(index);
                        lastPage = page;
                    }
                }
                cursorPos.index += increment;
            }
        }
        return current != null;
    }

    @Override
    public K next() {
        if(!hasNext()) {
            throw new NoSuchElementException();
        }
        current = null;
        return last;
    }

    /**
     * Get the last read key if there was one.
     *
     * @return the key or null
     */
    public K getKey() {
        return last;
    }

    /**
     * Get the last read value if there was one.
     *
     * @return the value or null
     */
    public V getValue() {
        return lastValue;
    }

    /**
     * Get the page where last retrieved key is located.
     *
     * @return the page
     */
    @SuppressWarnings("unused")
    Page<K,V> getPage() {
        return lastPage;
    }

    /**
     * Skip over that many entries. This method is relatively fast (for this map
     * implementation) even if many entries need to be skipped.
     *
     * @param n the number of entries to skip
     */
    public void skip(long n) {
        if (n < 10) {
            while (n-- > 0 && hasNext()) {
                next();
            }
        } else if(hasNext()) {
            assert cursorPos != null;
            CursorPos<K,V> cp = cursorPos;
            CursorPos<K,V> parent;
            while ((parent = cp.parent) != null) cp = parent;
            Page<K,V> root = cp.page;
            MVMap<K,V> map = root.map;
            long index = map.getKeyIndex(next());
            last = map.getKey(index + (reverse ? -n : n));
            this.cursorPos = traverseDown(root, last, reverse);
        }
    }

    /**
     * Fetch the next entry that is equal or larger than the given key, starting
     * from the given page. This method retains the stack.
     *
     * @param page to start from as a root
     * @param key to search for, null means search for the first available key
     */
    private static <K,V> CursorPos<K,V> traverseDown(Page<K, V> page, K key, boolean reverse) {
        CursorPos<K,V> cursorPos = key != null ? CursorPos.traverseDown(page, key) :
                reverse ? page.getAppendCursorPos(null) : page.getPrependCursorPos(null);
        int index = cursorPos.index;
        if (index < 0) {
            index = ~index;
            if (reverse) {
                --index;
            }
            cursorPos.index = index;
        }
        return cursorPos;
    }

    private static <K,V> int upperBound(Page<K,V> page) {
        return page.isLeaf() ? page.getKeyCount() : page.map.getChildPageCount(page);
    }
}
