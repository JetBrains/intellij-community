// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ClasspathCache {
  private static final double PROBABILITY = 0.005d;

  private final IntObjectHashMap resourcePackageCache = new IntObjectHashMap();
  private final IntObjectHashMap classPackageCache = new IntObjectHashMap();

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public interface IndexRegistrar {
    void registerPackageIndex(IntObjectHashMap classMap, IntObjectHashMap resourceMap, Loader loader);
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
    public void registerPackageIndex(IntObjectHashMap classMap, IntObjectHashMap resourceMap, Loader loader) {
      for (int classPackageHash : classPackageHashes) {
        addResourceEntry(classPackageHash, classMap, loader);
      }

      for (int resourcePackageHash : resourcePackageHashes) {
        addResourceEntry(resourcePackageHash, resourceMap, loader);
      }

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

    void transformClassNameAndAddPossiblyDuplicateNameEntry(@NotNull String name, int start) {
      int $ = name.indexOf('$', start);
      int end = $ == -1 ? name.lastIndexOf('.') : $;
      usedNameFingerprints.add(NameFilter.toNameFingerprint(name, start, end));
    }

    void addPossiblyDuplicateNameEntry(@NotNull String name, int start, int end) {
      usedNameFingerprints.add(NameFilter.toNameFingerprint(name, start, end));
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
      int uniques = usedNameFingerprints.size();
      if (uniques > 20_000) {
        // allow some growth for Idea main loader
        uniques += (int)(uniques * 0.03d);
      }
      NameFilter nameFilter = new NameFilter(uniques, PROBABILITY);
      StrippedLongOpenHashSet.SetIterator iterator = usedNameFingerprints.iterator();
      while (iterator.hasNext()) {
        nameFilter.addNameFingerprint(iterator.nextLong());
      }
      return nameFilter;
    }

    @Override
    public void registerPackageIndex(IntObjectHashMap classMap, IntObjectHashMap resourceMap, Loader loader) {
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
    classPackageCache.clear();
    resourcePackageCache.clear();
  }

  void applyLoaderData(@NotNull IndexRegistrar registrar, @NotNull Loader loader) {
    lock.writeLock().lock();
    try {
      registrar.registerPackageIndex(classPackageCache, resourcePackageCache, loader);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  Object getLoadersByName(@NotNull String resourcePath) {
    lock.readLock().lock();
    Object o;
    try {
      IntObjectHashMap map = resourcePath.endsWith(ClassPath.CLASS_EXTENSION) ? classPackageCache : resourcePackageCache;
      o = map.get(getPackageNameHash(resourcePath, resourcePath.lastIndexOf('/')));
    }
    finally {
      lock.readLock().unlock();
    }
    return o;
  }

  void collectLoaders(@NotNull String resourcePath, @NotNull Collection<Loader> loaders) {
    Object o = getLoadersByName(resourcePath);
    if (o instanceof Loader) {
      loaders.add((Loader)o);
    }
    else if (o != null) {
      Collections.addAll(loaders, (Loader[])o);
    }
  }

  @Nullable Resource iterateLoaders(@NotNull String resourcePath, @NotNull String name, @NotNull String shortName) {
    Object o = getLoadersByName(resourcePath);
    if (o instanceof Loader) {
      Loader loader = (Loader)o;
      if (loader.containsName(name, shortName)) {
        return ClassPath.findInLoader(loader, name);
      }
    }
    else if (o != null) {
      for (Loader loader : (Loader[])o) {
        if (loader.containsName(name, shortName)) {
          Resource result = ClassPath.findInLoader(loader, name);
          if (result != null) {
            return result;
          }
        }
      }
    }
    return null;
  }

  static int getPackageNameHash(@NotNull String resourcePath, int endIndex) {
    return endIndex <= 0 ? 0 : Murmur3_32Hash.MURMUR3_32.hashString(resourcePath, 0, endIndex);
  }

  public static void addResourceEntry(int hash, @NotNull IntObjectHashMap map, @NotNull Loader loader) {
    Object o = map.get(hash);
    if (o == null) {
      map.put(hash, loader);
    }
    else if (o instanceof Loader) {
      if (ClassPath.recordLoadingInfo) {
        assert loader != o;
      }
      map.put(hash, new Loader[]{(Loader)o, loader});
    }
    else {
      Loader[] loaderArray = (Loader[])o;
      if (ClassPath.recordLoadingInfo) {
        for (Loader value : loaderArray) {
          if (loader == value) {
            throw new IllegalStateException("Duplicated loader");
          }
        }
      }
      Loader[] newArray = Arrays.copyOf(loaderArray, loaderArray.length + 1);
      newArray[loaderArray.length] = loader;
      map.put(hash, newArray);
    }
  }

  static @NotNull String transformName(@NotNull String name) {
    if (name.isEmpty()) {
      return name;
    }

    int nameEnd = name.charAt(name.length() - 1) == '/' ? name.length() - 1 : name.length();
    name = name.substring(name.lastIndexOf('/', nameEnd - 1) + 1, nameEnd);

    if (name.endsWith(ClassPath.CLASS_EXTENSION)) {
      int $ = name.indexOf('$');
      return name.substring(0, $ == -1 ? name.lastIndexOf('.') : $);
    }
    return name;
  }

  static @NotNull String transformClassName(@NotNull String name) {
    int startIndex = name.lastIndexOf('.') + 1;
    int $ = name.indexOf('$', startIndex);
    return name.substring(startIndex, $ == -1 ? name.length() : $);
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
      int hash = MURMUR3_32_CUSTOM_SEED.hashString(name, 0, name.length());
      int hash2 = Murmur3_32Hash.MURMUR3_32.hashString(name, 0, name.length());
      return maybeContains(hash, hash2);
    }

    private static long toNameFingerprint(@NotNull String name, int start, int end) {
      int hash = MURMUR3_32_CUSTOM_SEED.hashString(name, start, end);
      int hash2 = Murmur3_32Hash.MURMUR3_32.hashString(name, start, end);
      return ((long)hash << 32) | (hash2 & 0xFFFFFFFFL);
    }
  }
}