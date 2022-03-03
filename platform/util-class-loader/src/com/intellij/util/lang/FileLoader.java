// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.xxh3.Xx3UnencodedString;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class FileLoader implements Loader {
  private static final EnumSet<StandardOpenOption> READ_OPTIONS = EnumSet.of(StandardOpenOption.READ);
  private static final EnumSet<StandardOpenOption> WRITE_OPTIONS = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

  private static final AtomicInteger totalLoaders = new AtomicInteger();
  private static final AtomicLong totalScanning = new AtomicLong();
  private static final AtomicLong totalSaving = new AtomicLong();
  private static final AtomicLong totalReading = new AtomicLong();

  private static final Boolean doFsActivityLogging = false;
  // find . -name "classpath.index" -delete
  private static final short indexFileVersion = 24;

  private final @NotNull Predicate<String> nameFilter;
  private final @NotNull Path path;

  FileLoader(@NotNull Path path) {
    this.path = path;
    this.nameFilter = __ -> true;
  }

  private FileLoader(@NotNull Path path, @NotNull Predicate<String> nameFilter) {
    this.path = path;
    this.nameFilter = nameFilter;
  }

  static @NotNull FileLoader createCachingFileLoader(Path file,
                                                     @Nullable CachePoolImpl cachePool,
                                                     Predicate<? super Path> cachingCondition,
                                                     boolean isClassPathIndexEnabled,
                                                     ClasspathCache cache) {
    if (cachePool == null) {
      LoaderData data = buildData(file, isClassPathIndexEnabled);
      FileLoader loader = new FileLoader(file, data.nameFilter);
      cache.applyLoaderData(data, loader);
      return loader;
    }

    ClasspathCache.IndexRegistrar data = cachePool.loaderIndexCache.get(file);
    if (data == null) {
      LoaderData loaderData = buildData(file, isClassPathIndexEnabled);
      FileLoader loader = new FileLoader(file, loaderData.nameFilter);
      if (cachingCondition != null && cachingCondition.test(file)) {
        cachePool.loaderIndexCache.put(file, loaderData);
      }
      cache.applyLoaderData(loaderData, loader);
      return loader;
    }
    else {
      FileLoader loader = new FileLoader(file, data.getNameFilter());
      cache.applyLoaderData(data, loader);
      return loader;
    }
  }

  @Override
  public @NotNull Path getPath() {
    return path;
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> fileNameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer)
    throws IOException {
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(path.resolve(dir))) {
      for (Path childPath : paths) {
        String name = path.relativize(childPath).toString();
        if (fileNameFilter.test(name) && Files.isRegularFile(childPath)) {
          try (InputStream stream = new BufferedInputStream(Files.newInputStream(childPath))) {
            consumer.accept(name, stream);
          }
        }
      }
    }
    catch (NotDirectoryException | NoSuchFileException ignore) {
    }
  }

  private static void buildPackageAndNameCache(Path startDir,
                                               ClasspathCache.LoaderDataBuilder context,
                                               StrippedLongArrayList nameHashes) {
    // FileVisitor is not used to avoid getting file attributes for class files
    // (.class extension is a strong indicator that it is file and not a directory)
    Deque<Path> dirCandidates = new ArrayDeque<>();
    dirCandidates.add(startDir);
    Path dir;
    int rootDirAbsolutePathLength = startDir.toString().length();
    while ((dir = dirCandidates.pollFirst()) != null) {
      try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
        boolean containsClasses = false;
        boolean containsResources = false;
        for (Path file : dirStream) {
          String path = getRelativeResourcePath(file.toString(), rootDirAbsolutePathLength);
          if (path.endsWith(ClasspathCache.CLASS_EXTENSION)) {
            nameHashes.add(Xx3UnencodedString.hashUnencodedString(path));
            containsClasses = true;
          }
          else {
            nameHashes.add(Xx3UnencodedString.hashUnencodedString(path));
            containsResources = true;
            if (!path.endsWith(".svg") && !path.endsWith(".png") && !path.endsWith(".xml")) {
              dirCandidates.addLast(file);
            }
          }
        }

        if (containsClasses || containsResources) {
          String relativeResourcePath = getRelativeResourcePath(dir.toString(), rootDirAbsolutePathLength);
          if (containsClasses) {
            context.addClassPackage(relativeResourcePath);
          }
          if (containsResources) {
            context.addResourcePackage(relativeResourcePath);
          }
        }
      }
      catch (NoSuchFileException | NotDirectoryException ignore) {
      }
      catch (IOException e) {
        //noinspection UseOfSystemOutOrSystemErr
        e.printStackTrace(System.err);
      }
    }
  }

  private static @NotNull String getRelativeResourcePath(@NotNull String path, int startPathLength) {
    String relativePath = path.substring(startPathLength).replace(File.separatorChar, '/');
    return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name) {
    Path file = path.resolve(name);
    return Files.exists(file) ? new FileResource(file) : null;
  }

  @Override
  public @Nullable Class<?> findClass(String fileName, String className, ClassPath.ClassDataConsumer classConsumer) throws IOException {
    Path file = path.resolve(fileName);
    byte[] data;
    try {
      data = Files.readAllBytes(file);
    }
    catch (NoSuchFileException e) {
      return null;
    }
    return classConsumer.consumeClassData(className, data, this);
  }

  private static @Nullable LoaderData readFromIndex(Path index) {
    long started = System.nanoTime();
    boolean isOk = false;
    short version = -1;
    try (SeekableByteChannel channel = Files.newByteChannel(index, READ_OPTIONS)) {
      ByteBuffer buffer = DirectByteBufferPool.DEFAULT_POOL.allocate((int)channel.size());
      try {
        do {
          channel.read(buffer);
        }
        while (buffer.hasRemaining());
        buffer.flip();

        // little endian - native order for Intel and Apple ARM
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        version = buffer.getShort();
        if (version == indexFileVersion) {
          long[] classPackageHashes = new long[buffer.getInt()];
          long[] resourcePackageHashes = new long[buffer.getInt()];
          LongBuffer longBuffer = buffer.asLongBuffer();
          longBuffer.get(classPackageHashes);
          longBuffer.get(resourcePackageHashes);
          buffer.position(buffer.position() + (longBuffer.position() * Long.BYTES));
          LoaderData loaderData = new LoaderData(resourcePackageHashes, classPackageHashes, new NameFilter(new Xor16(buffer)));
          isOk = true;
          return loaderData;
        }
      }
      finally {
        DirectByteBufferPool.DEFAULT_POOL.release(buffer);
      }
    }
    catch (NoSuchFileException ignore) {
      isOk = true;
    }
    catch (Exception e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Cannot read classpath index (version=" + version + ", module=" + index.getParent().getFileName() + ")");
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    finally {
      if (!isOk) {
        try {
          Files.deleteIfExists(index);
        }
        catch (IOException ignore) {
        }
      }
      totalReading.addAndGet(System.nanoTime() - started);
    }

    return null;
  }

  private static void saveIndex(@NotNull LoaderData data, @NotNull Path indexFile) throws IOException {
    long started = System.nanoTime();
    ByteBuffer buffer = DirectByteBufferPool.DEFAULT_POOL.allocate(Short.BYTES + data.approximateSizeInBytes());
    try {
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      buffer.putShort(indexFileVersion);
      data.save(buffer);
      assert buffer.remaining() == 0;
      buffer.flip();
      try (SeekableByteChannel channel = Files.newByteChannel(indexFile, WRITE_OPTIONS)) {
        do {
          channel.write(buffer);
        }
        while (buffer.hasRemaining());
      }
    }
    finally {
      DirectByteBufferPool.DEFAULT_POOL.release(buffer);
      totalSaving.addAndGet(System.nanoTime() - started);
    }
  }

  private static @NotNull LoaderData buildData(@NotNull Path path, boolean isClassPathIndexEnabled) {
    Path indexFile = isClassPathIndexEnabled ? path.resolve("classpath.index") : null;
    LoaderData loaderData = indexFile == null ? null : readFromIndex(indexFile);

    int nsMsFactor = 1_000_000;
    int currentLoaders = totalLoaders.incrementAndGet();
    long currentScanningTime;
    if (loaderData == null) {
      long started = System.nanoTime();

      StrippedLongArrayList nameHashes = new StrippedLongArrayList();

      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      buildPackageAndNameCache(path, loaderDataBuilder, nameHashes);
      loaderData = new LoaderData(loaderDataBuilder.resourcePackageHashes.toArray(),
                                  loaderDataBuilder.classPackageHashes.toArray(),
                                  new NameFilter(Xor16.construct(nameHashes.elements(), 0, nameHashes.size())));
      long doneNanos = System.nanoTime() - started;
      currentScanningTime = totalScanning.addAndGet(doneNanos);
      if (doFsActivityLogging) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Scanned: " + path + " for " + (doneNanos / nsMsFactor) + "ms");
      }

      if (isClassPathIndexEnabled) {
        try {
          Path tempFile = indexFile.getParent().resolve("classpath.index.tmp");
          saveIndex(loaderData, tempFile);
          try {
            Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
          }
          catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
          }
        }
        catch (IOException e) {
          //noinspection CallToPrintStackTrace
          e.printStackTrace();
        }
      }
    }
    else {
      currentScanningTime = totalScanning.get();
    }

    if (doFsActivityLogging) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Scanning: " + (currentScanningTime / nsMsFactor) + "ms" +
                         ", loading: " + (totalReading.get() / nsMsFactor) + "ms for " + currentLoaders + " loaders");
    }
    return loaderData;
  }

  private static final class FileResource implements Resource {
    private URL url;
    private final Path file;

    FileResource(@NotNull Path file) {
      this.file = file;
    }

    @Override
    public @NotNull URL getURL() {
      URL result = this.url;
      if (result == null) {
        try {
          result = file.toUri().toURL();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
        url = result;
      }
      return result;
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      return new BufferedInputStream(Files.newInputStream(file), 32_000);
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      return Files.readAllBytes(file);
    }

    @Override
    public String toString() {
      return file.toString();
    }
  }

  @Override
  public String toString() {
    return "FileLoader(path=" + path + ')';
  }

  @Override
  public boolean containsName(String name) {
    return nameFilter.test(name);
  }

  private static final class LoaderData implements ClasspathCache.IndexRegistrar {
    private final long[] resourcePackageHashes;
    private final long[] classPackageHashes;
    private final @NotNull NameFilter nameFilter;

    LoaderData(long[] resourcePackageHashes, long[] classPackageHashes, @NotNull NameFilter nameFilter) {
      this.resourcePackageHashes = resourcePackageHashes;
      this.classPackageHashes = classPackageHashes;
      this.nameFilter = nameFilter;
    }

    @Override
    public int classPackageCount() {
      return classPackageHashes.length;
    }

    @Override
    public int resourcePackageCount() {
      return resourcePackageHashes.length;
    }

    @Override
    public @NotNull Predicate<String> getNameFilter() {
      return nameFilter;
    }

    @Override
    public long[] classPackages() {
      return classPackageHashes;
    }

    @Override
    public long[] resourcePackages() {
      return resourcePackageHashes;
    }

    int approximateSizeInBytes() {
      return Integer.BYTES * 2 +
             classPackageHashes.length * Long.BYTES +
             resourcePackageHashes.length * Long.BYTES +
             nameFilter.filter.sizeInBytes();
    }

    void save(@NotNull ByteBuffer buffer) {
      buffer.putInt(classPackageHashes.length);
      buffer.putInt(resourcePackageHashes.length);
      LongBuffer longBuffer = buffer.asLongBuffer();
      longBuffer.put(classPackageHashes);
      longBuffer.put(resourcePackageHashes);
      buffer.position(buffer.position() + longBuffer.position() * Long.BYTES);
      nameFilter.filter.write(buffer);
    }
  }

  private static final class NameFilter implements Predicate<String> {
    final Xor16 filter;

    NameFilter(Xor16 filter) {
      this.filter = filter;
    }

    @Override
    public boolean test(String name) {
      if (name.isEmpty()) {
        return true;
      }

      int lastIndex = name.length() - 1;
      int end = name.charAt(lastIndex) == '/' ? lastIndex : name.length();
      return filter.mightContain(Xx3UnencodedString.hashUnencodedStringRange(name, 0, end));
    }
  }
}
