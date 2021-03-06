// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.BloomFilterBase;
import com.intellij.util.io.Murmur3_32Hash;
import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import com.intellij.util.lang.fastutil.StrippedLongOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ClasspathCache {
  private static final double PROBABILITY = 0.005d;
  private static final IntFunction<Loader[][]> ARRAY_FACTORY = size -> new Loader[size][];

  private volatile IntObjectHashMap<Loader[]> classPackageCache = new IntObjectHashMap<>(ARRAY_FACTORY);
  private volatile IntObjectHashMap<Loader[]> resourcePackageCache = new IntObjectHashMap<>(ARRAY_FACTORY);

  public interface IndexRegistrar {
    void registerPackageIndex(IntObjectHashMap<Loader[]> classMap, IntObjectHashMap<Loader[]> resourceMap, Loader loader);
  }

  public static final class LoaderData implements IndexRegistrar {
    private final int[] resourcePackageHashes;
    private final int[] classPackageHashes;
    private final NameFilter nameFilter;

    LoaderData(int[] resourcePackageHashes, int[] classPackageHashes, NameFilter nameFilter) {
      this.resourcePackageHashes = resourcePackageHashes;
      this.classPackageHashes = classPackageHashes;
      this.nameFilter = nameFilter;
    }

    int sizeInBytes() {
      return Integer.BYTES * 2 +
             classPackageHashes.length * Integer.BYTES +
             resourcePackageHashes.length * Integer.BYTES +
             nameFilter.sizeInBytes();
    }

    void save(@NotNull ByteBuffer buffer) throws IOException {
      buffer.putInt(classPackageHashes.length);
      buffer.putInt(resourcePackageHashes.length);
      IntBuffer intBuffer = buffer.asIntBuffer();
      intBuffer.put(classPackageHashes);
      intBuffer.put(resourcePackageHashes);
      buffer.position(buffer.position() + intBuffer.position() * Integer.BYTES);
      nameFilter.save(buffer);
    }

    @Override
    public void registerPackageIndex(IntObjectHashMap<Loader[]> classMap, IntObjectHashMap<Loader[]> resourceMap, Loader loader) {
      addResourceEntries(classPackageHashes, classMap, loader);
      addResourceEntries(resourcePackageHashes, resourceMap, loader);

      loader.setNameFilter(nameFilter);
    }
  }

  static final class LoaderDataBuilder implements IndexRegistrar {
    private final StrippedLongOpenHashSet usedNameFingerprints;
    private final StrippedIntOpenHashSet resourcePackageHashes = new StrippedIntOpenHashSet();
    private final StrippedIntOpenHashSet classPackageHashes = new StrippedIntOpenHashSet();

    LoaderDataBuilder(boolean isNameFilterRequired) {
      usedNameFingerprints = isNameFilterRequired ? new StrippedLongOpenHashSet() : null;
    }

    void andClassName(@NotNull String name) {
      usedNameFingerprints.add(NameFilter.toNameFingerprint(name, name.length()));
    }

    void addResourceName(@NotNull String name, int end) {
      usedNameFingerprints.add(NameFilter.toNameFingerprint(name, end));
    }

    void addResourcePackageFromName(@NotNull String path) {
      resourcePackageHashes.add(getPackageNameHash(path, path.lastIndexOf('/')));
    }

    void addResourcePackage(@NotNull String path) {
      resourcePackageHashes.add(getPackageNameHash(path, path.length()));
    }

    void addClassPackageFromName(@NotNull String path) {
      classPackageHashes.add(getPackageNameHash(path, path.lastIndexOf('/')));
    }

    void addClassPackage(@NotNull String path) {
      classPackageHashes.add(getPackageNameHash(path, path.length()));
    }

    @NotNull LoaderData build() {
      return new ClasspathCache.LoaderData(resourcePackageHashes.toArray(), classPackageHashes.toArray(), createNameFilter());
    }

    private @NotNull NameFilter createNameFilter() {
      NameFilter nameFilter = new NameFilter(usedNameFingerprints.size(), PROBABILITY);
      StrippedLongOpenHashSet.SetIterator iterator = usedNameFingerprints.iterator();
      while (iterator.hasNext()) {
        nameFilter.addNameFingerprint(iterator.nextLong());
      }
      return nameFilter;
    }

    @Override
    public void registerPackageIndex(IntObjectHashMap<Loader[]> classMap, IntObjectHashMap<Loader[]> resourceMap, Loader loader) {
      StrippedIntOpenHashSet.SetIterator classIterator = classPackageHashes.iterator();
      while (classIterator.hasNext()) {
        addResourceEntry(classIterator.nextInt(), classMap, loader);
      }

      StrippedIntOpenHashSet.SetIterator resourceIterator = resourcePackageHashes.iterator();
      while (resourceIterator.hasNext()) {
        addResourceEntry(resourceIterator.nextInt(), resourceMap, loader);
      }

      if (usedNameFingerprints != null) {
        loader.setNameFilter(createNameFilter());
      }
    }
  }

  void clearCache() {
    classPackageCache = new IntObjectHashMap<>(ARRAY_FACTORY);
    resourcePackageCache = new IntObjectHashMap<>(ARRAY_FACTORY);
  }

  // executed as part of synchronized getLoaderSlowPath - no concurrent write
  void applyLoaderData(@NotNull IndexRegistrar registrar, @NotNull Loader loader) {
    IntObjectHashMap<Loader[]> newClassPackageCache = new IntObjectHashMap<>(classPackageCache);
    IntObjectHashMap<Loader[]> newResourcePackageCache = new IntObjectHashMap<>(resourcePackageCache);
    registrar.registerPackageIndex(newClassPackageCache, newResourcePackageCache, loader);
    classPackageCache = newClassPackageCache;
    resourcePackageCache = newResourcePackageCache;
  }

  Loader @Nullable [] getLoadersByName(@NotNull String resourcePath) {
    IntObjectHashMap<Loader[]> map = resourcePath.endsWith(ClassPath.CLASS_EXTENSION) ? classPackageCache : resourcePackageCache;
    return map.get(getPackageNameHash(resourcePath, resourcePath.lastIndexOf('/')));
  }

  Loader @Nullable [] getClassLoadersByName(@NotNull String resourcePath) {
    return classPackageCache.get(getPackageNameHash(resourcePath, resourcePath.lastIndexOf('/')));
  }

  static int getPackageNameHash(@NotNull String resourcePath, int endIndex) {
    return endIndex <= 0 ? 0 : Murmur3_32Hash.MURMUR3_32.hashString(resourcePath, 0, endIndex);
  }

  public static void addResourceEntries(int[] hashes, @NotNull IntObjectHashMap<Loader[]> map, @NotNull Loader loader) {
    Loader[] singleArray = null;
    for (int hash : hashes) {
      int index = map.index(hash);
      Loader[] loaders = map.getByIndex(index, hash);
      if (loaders == null) {
        if (singleArray == null) {
          singleArray = new Loader[]{loader};
        }
        map.addByIndex(index, hash, singleArray);
      }
      else {
        Loader[] newList = new Loader[loaders.length + 1];
        System.arraycopy(loaders, 0, newList, 0, loaders.length);
        newList[loaders.length] = loader;
        map.replaceByIndex(index, hash, newList);
      }
    }
  }

  private static void addResourceEntry(int hash, @NotNull IntObjectHashMap<Loader[]> map, @NotNull Loader loader) {
    int index = map.index(hash);
    Loader[] loaders = map.getByIndex(index, hash);
    if (loaders == null) {
      map.addByIndex(index, hash, new Loader[]{loader});
    }
    else {
      if (ClassPath.recordLoadingInfo) {
        for (Loader value : loaders) {
          if (loader == value) {
            throw new IllegalStateException("Duplicated loader");
          }
        }
      }
      Loader[] newList = new Loader[loaders.length + 1];
      System.arraycopy(loaders, 0, newList, 0, loaders.length);
      newList[loaders.length] = loader;
      map.replaceByIndex(index, hash, newList);
    }
  }

  static final class NameFilter extends BloomFilterBase implements Predicate<String> {
    private static final Murmur3_32Hash MURMUR3_32_CUSTOM_SEED = new Murmur3_32Hash(85_486);

    NameFilter(int _maxElementCount, double probability) {
      super(_maxElementCount, probability);
    }

    NameFilter(@NotNull ByteBuffer buffer) throws IOException {
      super(buffer);
    }

    private void addNameFingerprint(long nameFingerprint) {
      int hash = (int)(nameFingerprint >> 32);
      int hash2 = (int)nameFingerprint;
      addIt(hash, hash2);
    }

    @Override
    public boolean test(@NotNull String name) {
      int end = name.endsWith("/") ? (name.length() - 1) : name.length();
      int hash = MURMUR3_32_CUSTOM_SEED.hashString(name, 0, end);
      int hash2 = Murmur3_32Hash.MURMUR3_32.hashString(name, 0, end);
      return maybeContains(hash, hash2);
    }

    private static long toNameFingerprint(@NotNull String name, int end) {
      int hash = MURMUR3_32_CUSTOM_SEED.hashString(name, 0, end);
      int hash2 = Murmur3_32Hash.MURMUR3_32.hashString(name, 0, end);
      return ((long)hash << 32) | (hash2 & 0xFFFFFFFFL);
    }
  }
}