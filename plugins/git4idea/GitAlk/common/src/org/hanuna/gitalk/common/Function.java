package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

/**
* @author erokhins
*/
public interface Function<K, V> {
    @NotNull
    public V get(@NotNull K key);
}
