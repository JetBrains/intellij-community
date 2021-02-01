// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.util.io.DirectByteBufferPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class FileLoader extends Loader {
  private static final EnumSet<StandardOpenOption> READ_OPTIONS = EnumSet.of(StandardOpenOption.READ);
  private static final  EnumSet<StandardOpenOption> WRITE_OPTIONS = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

  private static final AtomicInteger totalLoaders = new AtomicInteger();
  private static final AtomicLong totalScanning = new AtomicLong();
  private static final AtomicLong totalSaving = new AtomicLong();
  private static final AtomicLong totalReading = new AtomicLong();

  private static final Boolean doFsActivityLogging = false;
  // find . -name "classpath.index" -delete
  private static final short ourVersion = 23;

  private final int rootDirAbsolutePathLength;
  private final boolean isClassPathIndexEnabled;

  private static final BlockingDeque<Map.Entry<ClasspathCache.LoaderData, Path>> loaderDataToSave = new LinkedBlockingDeque<>();
  private static final AtomicBoolean isSaveThreadStarted = new AtomicBoolean();

  FileLoader(@NotNull Path path, boolean isClassPathIndexEnabled) {
    super(path);

    rootDirAbsolutePathLength = path.toString().length();
    this.isClassPathIndexEnabled = isClassPathIndexEnabled;
  }

  @Override
  void processResources(@NotNull String dir, @NotNull Predicate<? super String> fileNameFilter, @NotNull BiConsumer<? super String, ? super InputStream> consumer)
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

  @Override
  public @Nullable Map<Loader.Attribute, String> getAttributes() throws IOException {
    return null;
  }

  private void buildPackageCache(@NotNull Path startDir, @NotNull ClasspathCache.LoaderDataBuilder context) {
    // FileVisitor is not used to avoid getting file attributes for class files
    // (.class extension is a strong indicator that it is file and not a directory)
    Deque<Path> dirCandidates = new ArrayDeque<>();
    dirCandidates.add(startDir);
    Path dir;
    while ((dir = dirCandidates.pollFirst()) != null) {
      try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
        boolean containsClasses = false;
        boolean containsResources = false;
        for (Path file : dirStream) {
          String path = startDir.relativize(file).toString().replace(File.separatorChar, '/');
          if (path.endsWith(ClassPath.CLASS_EXTENSION)) {
            context.andClassName(path);
            containsClasses = true;
          }
          else {
            context.addResourceName(path, path.length());
            containsResources = true;
            if (!path.endsWith(".svg") && !path.endsWith(".png") && !path.endsWith(".xml")) {
              dirCandidates.addLast(file);
            }
          }
        }

        if (containsClasses || containsResources) {
          String relativeResourcePath = getRelativeResourcePath(dir.toString());
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

  private @NotNull String getRelativeResourcePath(@NotNull String absFilePath) {
    String relativePath = absFilePath.substring(rootDirAbsolutePathLength);
    relativePath = relativePath.replace(File.separatorChar, '/');
    relativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    return relativePath;
  }

  @Override
  @Nullable Resource getResource(@NotNull String name) {
    Path file = path.resolve(name);
    return Files.exists(file) ? new FileResource(file) : null;
  }

  @Override
  @Nullable Class<?> findClass(@NotNull String fileName, String className, ClassPath.ClassDataConsumer classConsumer) throws IOException {
    Path file = path.resolve(fileName);
    byte[] data;
    try {
      data = Files.readAllBytes(file);
    }
    catch (NoSuchFileException e) {
      return null;
    }
    return classConsumer.consumeClassData(className, data, this, null);
  }

  private static ClasspathCache.LoaderData readFromIndex(Path index) {
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
        if (version == ourVersion) {
          int[] classPackageHashes = new int[buffer.getInt()];
          int[] resourcePackageHashes = new int[buffer.getInt()];
          IntBuffer intBuffer = buffer.asIntBuffer();
          intBuffer.get(classPackageHashes);
          intBuffer.get(resourcePackageHashes);
          buffer.position(buffer.position() + intBuffer.position() * Integer.BYTES);
          ClasspathCache.LoaderData loaderData =
            new ClasspathCache.LoaderData(resourcePackageHashes, classPackageHashes, new ClasspathCache.NameFilter(buffer));
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
      LoggerRt.getInstance(FileLoader.class).warn("Cannot read classpath index (version=" + version + ", module=" + index.getParent().getFileName() + ")", e);
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

  private static void saveToIndex(@NotNull ClasspathCache.LoaderData data, @NotNull Path indexFile) throws IOException {
    long started = System.nanoTime();
    boolean isOk = false;
    SeekableByteChannel channel = null;
    try {
      ByteBuffer buffer = DirectByteBufferPool.DEFAULT_POOL.allocate(Short.BYTES + data.sizeInBytes());
      try {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort(ourVersion);
        data.save(buffer);
        assert buffer.remaining() == 0;
        buffer.flip();
        channel = Files.newByteChannel(indexFile, WRITE_OPTIONS);
        do {
          channel.write(buffer);
        }
        while (buffer.hasRemaining());

        isOk = true;
      }
      finally {
        DirectByteBufferPool.DEFAULT_POOL.release(buffer);
      }
    }
    finally {
      if (channel != null) {
        try {
          channel.close();
        }
        catch (Exception e) {
          if (isOk) {
            LoggerRt.getInstance(FileLoader.class).warn(e);
          }
        }
      }
      if (!isOk) {
        try {
          Files.deleteIfExists(indexFile);
        }
        catch (IOException ignore) {
        }
      }
      totalSaving.addAndGet(System.nanoTime() - started);
    }
  }

  private Path getIndexFileFile() {
    return path.resolve("classpath.index");
  }

  @Override
  public @NotNull ClasspathCache.IndexRegistrar buildData() {
    ClasspathCache.LoaderData loaderData = null;
    Path indexFile = isClassPathIndexEnabled ? getIndexFileFile() : null;
    if (indexFile != null) {
      loaderData = readFromIndex(indexFile);
    }

    int nsMsFactor = 1000000;
    int currentLoaders = totalLoaders.incrementAndGet();
    long currentScanningTime;
    if (loaderData == null) {
      long started = System.nanoTime();

      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder(true);
      buildPackageCache(path, loaderDataBuilder);
      loaderData = loaderDataBuilder.build();
      long doneNanos = System.nanoTime() - started;
      currentScanningTime = totalScanning.addAndGet(doneNanos);
      if (doFsActivityLogging) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Scanned: " + path + " for " + (doneNanos / nsMsFactor) + "ms");
      }

      if (indexFile != null) {
        loaderDataToSave.addLast(new AbstractMap.SimpleImmutableEntry<>(loaderData, indexFile));
        startCacheSavingIfNeeded();
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

  private static void startCacheSavingIfNeeded() {
    if (!isSaveThreadStarted.compareAndSet(false, true)) {
      return;
    }

    //noinspection SSBasedInspection
    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
      new Thread(() -> {
        while (true) {
          try {
            Map.Entry<ClasspathCache.LoaderData, Path> entry = loaderDataToSave.takeFirst();
            Path finalFile = entry.getValue();
            Path tempFile = finalFile.getParent().resolve("classpath.index.tmp");
            try {
              saveToIndex(entry.getKey(), tempFile);
              try {
                Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
              }
              catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
              }
            }
            catch (Exception e) {
              LoggerRt.getInstance(FileLoader.class).warn("Cannot save classpath index for module " + finalFile.getParent().getFileName(), e);
            }
          }
          catch (InterruptedException ignored) {
            break;
          }
        }
      }, "Save classpath indexes for file loader").start();
    }, 10, TimeUnit.SECONDS);
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
}
