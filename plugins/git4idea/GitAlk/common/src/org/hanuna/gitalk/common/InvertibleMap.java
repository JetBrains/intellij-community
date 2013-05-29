package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author erokhins
 */
public interface InvertibleMap<K, V> {
    /**
     *
     * This map contains pair <K, V>, for every K exist only 1 V.
     * If put existed key, old value will be removed.
     * Main method is getKeys!
     *
     */

    public void put(@NotNull K key, @NotNull V value);

    // if value doesn't exist - return null
    @Nullable
    public V get(@NotNull K key);

    // return get(key) & remove value from map
    @Nullable
    public V remove(@NotNull K key);

    // return all keys which get(key) equals value
    @NotNull
    public Set<K> getKeys(@NotNull V value);

}
