// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Helper class that resolves the transitive closure of parent classloaders for a plugin classloader.
 * Implements multi-parent search with caching for performance.
 */
@SuppressWarnings("BoundedWildcard")
@ApiStatus.Internal
public final class MultiParentClassLoaderHelper {
  private static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];
  private static final AtomicInteger parentListCacheIdCounter = new AtomicInteger();

  private final @NotNull Supplier<@NotNull @Unmodifiable List<@NotNull ClassLoader>> directParentsSupplier;
  private final @NotNull ClassLoader coreLoader;

  // cache of a computed list of all parents (not only direct)
  private volatile ClassLoader @Nullable [] allParents;
  private volatile int allParentsLastCacheId = 0;

  public MultiParentClassLoaderHelper(@NotNull Supplier<@NotNull @Unmodifiable List<@NotNull ClassLoader>> directParentsSupplier,
                                      @NotNull ClassLoader coreLoader) {
    this.directParentsSupplier = directParentsSupplier;
    this.coreLoader = coreLoader;
  }

  /**
   * Returns a flat array of all parent classloaders (direct and transitive).
   * The result is cached and recomputed only when the cache is invalidated.
   */
  @ApiStatus.Internal
  public ClassLoader @NotNull [] getAllParents() {
    // todo https://youtrack.jetbrains.com/issue/IJPL-214092

    ClassLoader[] result = allParents;
    if (result != null && allParentsLastCacheId == parentListCacheIdCounter.get()) {
      return result;
    }

    List<ClassLoader> directParents = directParentsSupplier.get();
    if (directParents.isEmpty()) {
      result = new ClassLoader[]{coreLoader};
      allParents = result;
      return result;
    }

    LinkedHashSet<ClassLoader> parentSet = new LinkedHashSet<>();
    Deque<ClassLoader> queue = new ArrayDeque<>(directParents);

    while (true) {
      ClassLoader classLoader = queue.pollFirst();
      if (classLoader == null) {
        break;
      }
      if (!parentSet.add(classLoader)) {
        continue;
      }
      if (classLoader instanceof MultiParentClassLoaderSupport) {
        ((MultiParentClassLoaderSupport)classLoader).collectDirectParents(queue);
      }
    }

    parentSet.add(coreLoader);
    result = parentSet.toArray(EMPTY_CLASS_LOADER_ARRAY);
    allParents = result;
    allParentsLastCacheId = parentListCacheIdCounter.get();
    return result;
  }

  public void collectClassLoaders(@NotNull Deque<ClassLoader> queue) {
    queue.addAll(directParentsSupplier.get());
  }

  public void clearCache() {
    allParents = null;
  }

  /**
   * Searches for a resource as bytes in parent classloaders.
   * Iterates through all parent classloaders and attempts to load the resource.
   *
   * @param name the resource name
   * @return resource bytes or null if not found
   */
  public byte @Nullable [] getResourceAsBytes(@NotNull ClassPath ownerClassPath, @NotNull String name, boolean checkParents) {
    try {
      Resource resource = ownerClassPath.findResource(name);
      if (resource != null) {
        return resource.getBytes();
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    if (!checkParents) {
      return null;
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof UrlClassLoader) {
        Resource resource = ((UrlClassLoader)classloader).getClassPath().findResource(name);
        if (resource != null) {
          try {
            return resource.getBytes();
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
      else {
        try (InputStream input = classloader.getResourceAsStream(name)) {
          if (input != null) {
            // don't care about performance here - classloader should be UrlClassLoader
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = input.read(data, 0, data.length)) != -1) {
              buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
          }
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    return null;
  }

  /**
   * Finds a single resource URL in the owner's classpath or parent classloaders.
   * Optimized method that stops at first match.
   *
   * @param ownerClassPath the owning ClassPath
   * @param name the resource name (must be already canonicalized)
   * @return resource URL or null if not found
   */
  public @Nullable URL findResource(@NotNull ClassPath ownerClassPath, @NotNull String name) {
    Resource resource = ownerClassPath.findResource(name);
    if (resource != null) {
      return resource.getURL();
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof UrlClassLoader) {
        resource = ((UrlClassLoader)classloader).getClassPath().findResource(name);
        if (resource != null) {
          return resource.getURL();
        }
      }
      else {
        URL url = classloader.getResource(name);
        if (url != null) {
          return url;
        }
      }
    }

    return null;
  }

  /**
   * Gets a resource as an InputStream from the owner's classpath or parent classloaders.
   * Optimized method that stops at first match.
   *
   * @param ownerClassPath the owning ClassPath
   * @param name the resource name (must be already canonicalized)
   * @return resource input stream or null if not found, or throws UncheckedIOException on error
   */
  public @Nullable InputStream getResourceAsStream(@NotNull ClassPath ownerClassPath, @NotNull String name) {
    Resource resource = ownerClassPath.findResource(name);
    if (resource != null) {
      try {
        return resource.getInputStream();
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof UrlClassLoader) {
        resource = ((UrlClassLoader)classloader).getClassPath().findResource(name);
        if (resource != null) {
          try {
            return resource.getInputStream();
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
      else {
        InputStream stream = classloader.getResourceAsStream(name);
        if (stream != null) {
          return stream;
        }
      }
    }
    return null;
  }

  /**
   * Finds all resources with the given name in parent classloaders.
   * Iterates through all parent classloaders and collects resources.
   *
   * @param ownerClassPath the owning ClassPath
   * @param name the resource name
   * @return enumeration of all matching resource URLs
   */
  public @NotNull Enumeration<URL> findResources(@NotNull ClassPath ownerClassPath, @NotNull String name) {
    List<Enumeration<URL>> resources = new ArrayList<>();
    resources.add(ownerClassPath.getResources(name));

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof UrlClassLoader) {
        resources.add(((UrlClassLoader)classloader).getClassPath().getResources(name));
      }
      else {
        try {
          resources.add(classloader.getResources(name));
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    return new DeepEnumeration(resources);
  }

  /**
   * Enumeration implementation that chains multiple enumerations together.
   */
  private static final class DeepEnumeration implements Enumeration<URL> {
    private final List<Enumeration<URL>> list;
    private int index = 0;

    DeepEnumeration(List<Enumeration<URL>> list) {
      this.list = list;
    }

    @Override
    public boolean hasMoreElements() {
      while (index < list.size()) {
        Enumeration<URL> e = list.get(index);
        if (e.hasMoreElements()) {
          return true;
        }
        index++;
      }
      return false;
    }

    @Override
    public URL nextElement() {
      if (!hasMoreElements()) {
        throw new NoSuchElementException();
      }
      return list.get(index).nextElement();
    }
  }
}
