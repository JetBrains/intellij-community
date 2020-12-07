// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.BloomFilterBase;
import com.intellij.util.lang.fastutil.StrippedIntOpenHashSet;
import com.intellij.util.lang.fastutil.StrippedLongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class ClasspathCache {
  static final int NUMBER_OF_ACCESSES_FOR_LAZY_CACHING = 1000;
  private static final double PROBABILITY = 0.005d;

  private final IntObjectHashMap resourcePackagesCache = new IntObjectHashMap();
  private final IntObjectHashMap classPackagesCache = new IntObjectHashMap();

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  static final class LoaderData {
    private final int[] resourcePackageHashes;
    private final int[] classPackageHashes;
    private final NameFilter myNameFilter;

    LoaderData(int @NotNull [] resourcePackageHashes, int @NotNull [] classPackageHashes, @NotNull NameFilter nameFilter) {
      this.resourcePackageHashes = resourcePackageHashes;
      this.classPackageHashes = classPackageHashes;
      myNameFilter = nameFilter;
    }

    LoaderData(@NotNull DataInput dataInput) throws IOException {
      this(readIntList(dataInput), readIntList(dataInput), new ClasspathCache.NameFilter(dataInput));
    }

    private static int @NotNull [] readIntList(@NotNull DataInput reader) throws IOException {
      int numberOfElements = DataInputOutputUtilRt.readINT(reader);
      int[] ints = new int[numberOfElements];
      for (int i = 0; i < numberOfElements; ++i) {
        ints[i] = DataInputOutputUtilRt.readINT(reader);
      }
      return ints;
    }

    void save(@NotNull DataOutput dataOutput) throws IOException {
      writeIntArray(dataOutput, resourcePackageHashes);
      writeIntArray(dataOutput, classPackageHashes);
      myNameFilter.save(dataOutput);
    }

    private static void writeIntArray(@NotNull DataOutput writer, int @NotNull [] hashes) throws IOException {
      DataInputOutputUtilRt.writeINT(writer, hashes.length);
      for (int hash : hashes) {
        DataInputOutputUtilRt.writeINT(writer, hash);
      }
    }

    @NotNull NameFilter getNameFilter() {
      return myNameFilter;
    }
  }

  static final class LoaderDataBuilder {
    private final StrippedLongOpenHashSet myUsedNameFingerprints = new StrippedLongOpenHashSet();
    private final StrippedIntOpenHashSet myResourcePackageHashes = new StrippedIntOpenHashSet();
    private final StrippedIntOpenHashSet myClassPackageHashes = new StrippedIntOpenHashSet();

    void addPossiblyDuplicateNameEntry(@NotNull String name) {
      name = transformName(name);
      myUsedNameFingerprints.add(NameFilter.toNameFingerprint(name));
    }

    void addResourcePackageFromName(@NotNull String path) {
      myResourcePackageHashes.add(getPackageNameHash(path, path.lastIndexOf('/')));
    }

    void addResourcePackage(@NotNull String path) {
      myResourcePackageHashes.add(getPackageNameHash(path, path.length()));
    }

    void addClassPackageFromName(@NotNull String path) {
      myClassPackageHashes.add(getPackageNameHash(path, path.lastIndexOf('/')));
    }

    void addClassPackage(@NotNull String path) {
      myClassPackageHashes.add(getPackageNameHash(path, path.length()));
    }

    @NotNull LoaderData build() {
      int uniques = myUsedNameFingerprints.size();
      if (uniques > 20000) {
        // allow some growth for Idea main loader
        uniques += (int)(uniques * 0.03d);
      }
      NameFilter nameFilter = new NameFilter(uniques, PROBABILITY);
      StrippedLongOpenHashSet.SetIterator iterator = myUsedNameFingerprints.iterator();
      while (iterator.hasNext()) {
        nameFilter.addNameFingerprint(iterator.nextLong());
      }
      return new ClasspathCache.LoaderData(myResourcePackageHashes.toArray(), myClassPackageHashes.toArray(), nameFilter);
    }
  }

  void clearCache() {
    classPackagesCache.clear();
    resourcePackagesCache.clear();
  }

  void applyLoaderData(@NotNull LoaderData loaderData, @NotNull Loader loader) {
    lock.writeLock().lock();
    try {
      for (int resourcePackageHash : loaderData.resourcePackageHashes) {
        addResourceEntry(resourcePackageHash, resourcePackagesCache, loader);
      }

      for (int classPackageHash : loaderData.classPackageHashes) {
        addResourceEntry(classPackageHash, classPackagesCache, loader);
      }

      loader.applyData(loaderData);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  interface LoaderIterator <R, T1, T2> {
    @Nullable R process(@NotNull Loader loader, @NotNull T1 parameter, @NotNull T2 parameter2, @NotNull String shortName);
  }

  @Nullable <R, T1, T2> R iterateLoaders(@NotNull String resourcePath,
                                         @NotNull LoaderIterator<R, T1, T2> iterator,
                                         @NotNull T1 parameter,
                                         @NotNull T2 parameter2,
                                         @NotNull String shortName) {
    lock.readLock().lock();
    Object o;
    try {
      IntObjectHashMap map = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION) ? classPackagesCache : resourcePackagesCache;
      o = map.get(getPackageNameHash(resourcePath, resourcePath.lastIndexOf('/')));
    }
    finally {
      lock.readLock().unlock();
    }

    if (o == null) {
      return null;
    }
    if (o instanceof Loader) {
      return iterator.process((Loader)o, parameter, parameter2, shortName);
    }

    for (Loader l : (Loader[])o) {
      R result = iterator.process(l, parameter, parameter2, shortName);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  static int getPackageNameHash(@NotNull String resourcePath, int endIndex) {
    int h = 0;
    for (int off = 0; off < endIndex; off++) {
      h = 31 * h + resourcePath.charAt(off);
    }
    return h;
  }

  private static void addResourceEntry(int hash, @NotNull IntObjectHashMap map, @NotNull Loader loader) {
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
      Loader[] loadersArray = (Loader[])o;
      if (ClassPath.recordLoadingInfo) {
        assert ArrayUtilRt.indexOf(loadersArray, loader, 0, loadersArray.length) == -1;
      }
      Loader[] newArray = Arrays.copyOf(loadersArray, loadersArray.length + 1);
      newArray[loadersArray.length] = loader;
      map.put(hash, newArray);
    }
  }

  static @NotNull String transformName(@NotNull String name) {
    int nameEnd = !name.isEmpty() && name.charAt(name.length() - 1) == '/' ? name.length() - 1 : name.length();
    name = name.substring(name.lastIndexOf('/', nameEnd-1) + 1, nameEnd);

    if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
      String name1 = name;
      int $ = name1.indexOf('$');
      if ($ != -1) {
        name1 = name1.substring(0, $);
      }
      else {
        int index = name1.lastIndexOf('.');
        if (index >= 0) name1 = name1.substring(0, index);
      }
      name = name1;
    }
    return name;
  }

  static final class NameFilter extends BloomFilterBase {
    private static final int SEED = 31;

    NameFilter(int _maxElementCount, double probability) {
      super(_maxElementCount, probability);
    }

    NameFilter(@NotNull DataInput input) throws IOException {
      super(input);
    }

    private void addNameFingerprint(long nameFingerprint) {
      int hash = (int)(nameFingerprint >> 32);
      int hash2 = (int)nameFingerprint;
      addIt(hash, hash2);
    }

    boolean maybeContains(@NotNull String name) {
      int hash = name.hashCode();
      int hash2 = StringHash.murmur(name, SEED);
      return maybeContains(hash, hash2);
    }

    @Override
    protected void save(@NotNull DataOutput output) throws IOException {
      super.save(output);
    }

    private static long toNameFingerprint(@NotNull String name) {
      int hash = name.hashCode();
      int hash2 = StringHash.murmur(name, SEED);
      return ((long)hash << 32) | (hash2 & 0xFFFFFFFFL);
    }
  }
}