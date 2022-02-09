// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.xxh3.Xx3UnencodedString;

import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class ClasspathCache {
  public static final String CLASS_EXTENSION = ".class";

  private static final IntFunction<Loader[][]> ARRAY_FACTORY = size -> new Loader[size][];

  private StrippedLongToObjectMap<Loader[]> classPackageCache;
  private StrippedLongToObjectMap<Loader[]> resourcePackageCache;

  private static final LongFunction<Loader[]> NULL = value -> null;
  private volatile LongFunction<Loader[]> classPackageCacheGetter = NULL;
  private volatile LongFunction<Loader[]> resourcePackageCacheGetter = NULL;

  public interface IndexRegistrar {
    default Predicate<String> getNameFilter() {
      return null;
    }

    int classPackageCount();

    int resourcePackageCount();

    long[] classPackages();

    long[] resourcePackages();

    default @Nullable LongPredicate getKeyFilter(boolean forClasses) {
      return null;
    }
  }

  static final class LoaderDataBuilder implements IndexRegistrar {
    final StrippedLongSet classPackageHashes = new StrippedLongSet();
    final StrippedLongSet resourcePackageHashes = new StrippedLongSet();

    @Override
    public int classPackageCount() {
      return classPackageHashes.size();
    }

    @Override
    public int resourcePackageCount() {
      return resourcePackageHashes.size();
    }

    @Override
    public long[] classPackages() {
      return classPackageHashes.keys;
    }

    @Override
    public long[] resourcePackages() {
      return resourcePackageHashes.keys;
    }

    @Override
    public LongPredicate getKeyFilter(boolean forClasses) {
      return new LongPredicate() {
        boolean addZero = forClasses ? classPackageHashes.hasNull() : resourcePackageHashes.hasNull();

        @Override
        public boolean test(long it) {
          if (it == 0) {
            if (!addZero) {
              return false;
            }

            addZero = false;
          }
          return true;
        }
      };
    }

    void addResourcePackage(@NotNull String path) {
      resourcePackageHashes.add(getPackageNameHash(path, path.length()));
    }

    void addPackageFromName(@NotNull String path) {
      StrippedLongSet set = path.endsWith(CLASS_EXTENSION) ? classPackageHashes : resourcePackageHashes;
      set.add(getPackageNameHash(path, path.lastIndexOf('/')));
    }

    void addClassPackage(@NotNull String path) {
      classPackageHashes.add(getPackageNameHash(path, path.length()));
    }
  }

  void clearCache() {
    classPackageCacheGetter = NULL;
    resourcePackageCacheGetter = NULL;
    classPackageCache = null;
    resourcePackageCache = null;
  }

  // executed as part of synchronized getLoaderSlowPath - not a concurrent write
  void applyLoaderData(@NotNull IndexRegistrar registrar, @NotNull Loader loader) {
    if (registrar.classPackageCount() != 0) {
      StrippedLongToObjectMap<Loader[]> newClassMap = classPackageCache == null
                                                      ? new StrippedLongToObjectMap<>(ARRAY_FACTORY, registrar.classPackageCount())
                                                      : new StrippedLongToObjectMap<>(classPackageCache);
      addPackages(registrar.classPackages(), newClassMap, loader, registrar.getKeyFilter(true));
      classPackageCache = newClassMap;
      classPackageCacheGetter = newClassMap;
    }
    if (registrar.resourcePackageCount() != 0) {
      StrippedLongToObjectMap<Loader[]> newResourceMap = resourcePackageCache == null
                                                         ? new StrippedLongToObjectMap<>(ARRAY_FACTORY, registrar.resourcePackageCount())
                                                         : new StrippedLongToObjectMap<>(resourcePackageCache);
      resourcePackageCache = newResourceMap;
      resourcePackageCacheGetter = newResourceMap;
      addPackages(registrar.resourcePackages(), newResourceMap, loader, registrar.getKeyFilter(false));
    }
  }

  Loader @Nullable [] getLoadersByName(@NotNull String path) {
    return (path.endsWith(CLASS_EXTENSION)
            ? classPackageCacheGetter
            : resourcePackageCacheGetter).apply(getPackageNameHash(path, path.lastIndexOf('/')));
  }

  Loader @Nullable [] getLoadersByResourcePackageDir(@NotNull String resourcePath) {
    return resourcePackageCacheGetter.apply(getPackageNameHash(resourcePath, resourcePath.length()));
  }

  Loader @Nullable [] getClassLoadersByPackageNameHash(long packageNameHash) {
    return classPackageCacheGetter.apply(packageNameHash);
  }

  public static long getPackageNameHash(@NotNull String resourcePath, int endIndex) {
    return endIndex <= 0 ? 0 : Xx3UnencodedString.hashUnencodedStringRange(resourcePath, 0, endIndex);
  }

  private static void addPackages(long[] hashes, StrippedLongToObjectMap<Loader[]> map, Loader loader, @Nullable LongPredicate hashFilter) {
    Loader[] singleArray = null;
    for (long hash : hashes) {
      if (hashFilter != null && !hashFilter.test(hash)) {
        continue;
      }

      int index = map.index(hash);
      if (index < 0) {
        if (singleArray == null) {
          singleArray = new Loader[]{loader};
        }
        map.addByIndex(index, hash, singleArray);
      }
      else {
        Loader[] loaders = map.getByIndex(index);
        Loader[] newList = new Loader[loaders.length + 1];
        System.arraycopy(loaders, 0, newList, 0, loaders.length);
        newList[loaders.length] = loader;
        map.replaceByIndex(index, hash, newList);
      }
    }
  }
}