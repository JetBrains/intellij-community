// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class FileLoader extends Loader {
  private static final AtomicInteger totalLoaders = new AtomicInteger();
  private static final AtomicLong totalScanning = new AtomicLong();
  private static final AtomicLong totalSaving = new AtomicLong();
  private static final AtomicLong totalReading = new AtomicLong();

  private static final Boolean doFsActivityLogging = false;
  private static final int ourVersion = 1;

  private final Path rootDir;
  private final int rootDirAbsolutePathLength;
  private final ClassPath configuration;

  private final DirEntry root = new DirEntry(0, "");

  FileLoader(@NotNull Path path, @NotNull ClassPath configuration) {
    super(path);

    rootDir = path;
    rootDirAbsolutePathLength = rootDir.toString().length();
    this.configuration = configuration;
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
          String name = file.getFileName().toString();
          context.addPossiblyDuplicateNameEntry(name);
          if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
            containsClasses = true;
          }
          else {
            containsResources = true;
            if (!name.endsWith(".svg") && !name.endsWith(".png")) {
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

  private static final class DirEntry {
    static final int[] EMPTY = new int[0];
    volatile int[] childrenNameHashes;

    volatile DirEntry[] childrenDirectories;
    final int nameHash;
    final @NotNull String name;

    DirEntry(int nameHash, @NotNull String name) {
      this.nameHash = nameHash;
      this.name = name;
    }
  }

  @Override
  @Nullable Resource getResource(@NotNull String name) {
    try {
      if (configuration.lazyClassloadingCaches) {
        DirEntry lastEntry = root;
        // 0 .. prevIndex is package
        int prevIndex = 0;
        int nextIndex = name.indexOf('/', prevIndex);

        while (true) {
          // prevIndex, nameEnd is package or class name
          int nameEnd = nextIndex == -1 ? name.length() : nextIndex;
          int nameHash = StringUtilRt.stringHashCodeInsensitive(name, prevIndex, nameEnd, 0);
          if (!nameHashIsPresentInChildren(lastEntry, name, prevIndex, nameHash)) {
            return null;
          }
          if (nextIndex == -1 || nextIndex == name.length() - 1) {
            break;
          }

          lastEntry = findOrCreateNextDirEntry(lastEntry, name, prevIndex, nameEnd, nameHash);
          prevIndex = nextIndex + 1;
          nextIndex = name.indexOf('/', prevIndex);
        }
      }

      Path file = rootDir.resolve(name);
      if (Files.exists(file)) {
        return new FileResource(file);
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  private static @NotNull DirEntry findOrCreateNextDirEntry(@NotNull DirEntry lastEntry, @NotNull String name,
                                                            int prevIndex,
                                                            int nameEnd,
                                                            int nameHash) {
    DirEntry nextEntry = null;
    DirEntry[] directories = lastEntry.childrenDirectories; // volatile read

    if (directories != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int index = 0, len = directories.length; index < len; ++index) {
        DirEntry previouslyScannedDir = directories[index];
        if (previouslyScannedDir.nameHash == nameHash &&
            previouslyScannedDir.name.regionMatches(0, name, prevIndex, nameEnd - prevIndex)) {
          nextEntry = previouslyScannedDir;
          break;
        }
      }
    }

    if (nextEntry == null) {
      nextEntry = new DirEntry(nameHash, name.substring(prevIndex, nameEnd));
      DirEntry[] newChildrenDirectories;
      if (directories != null) {
        newChildrenDirectories = Arrays.copyOf(directories, directories.length + 1);
        newChildrenDirectories[directories.length] = nextEntry;
      }
      else {
        newChildrenDirectories = new DirEntry[]{nextEntry};
      }
      // volatile write with new copy of data
      lastEntry.childrenDirectories = newChildrenDirectories;
    }

    lastEntry = nextEntry;
    return lastEntry;
  }

  private boolean nameHashIsPresentInChildren(@NotNull DirEntry lastEntry, @NotNull String name, int prevIndex, int nameHash) {
    // volatile read
    int[] childrenNameHashes = lastEntry.childrenNameHashes;
    if (childrenNameHashes == null) {
      Path d = prevIndex == 0 ? rootDir : rootDir.resolve(name.substring(0, prevIndex));
      List<String> list;
      try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(d)) {
        list = new ArrayList<>();
        for (Path child : dirStream) {
          list.add(child.toString());
        }

        childrenNameHashes = new int[list.size()];
        for (int i = 0, n = list.size(); i < n; ++i) {
          String chars = list.get(i);
          childrenNameHashes[i] = StringUtilRt.stringHashCodeInsensitive(chars, 0, chars.length(), 0);
        }
      }
      catch (IOException e) {
        childrenNameHashes = DirEntry.EMPTY;
      }

      // volatile write
      lastEntry.childrenNameHashes = childrenNameHashes;
    }

    for (int childNameHash : childrenNameHashes) {
      if (childNameHash == nameHash) {
        return true;
      }
    }
    return false;
  }

  private ClasspathCache.LoaderData tryReadFromIndex() {
    if (!configuration.isClassPathIndexEnabled) {
      return null;
    }

    long started = System.nanoTime();
    Path index = getIndexFileFile();
    boolean isOk = false;
    try (DataInputStream reader = new DataInputStream(new BufferedInputStream(Files.newInputStream(index), 32_000))) {
      if (DataInputOutputUtilRt.readINT(reader) == ourVersion) {
        ClasspathCache.LoaderData loaderData = new ClasspathCache.LoaderData(reader);
        isOk = true;
        return loaderData;
      }
    }
    catch (NoSuchFileException ignore) {
      isOk = true;
    }
    catch (IOException ignore) {
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

  private void trySaveToIndex(@NotNull ClasspathCache.LoaderData data) {
    if (!configuration.isClassPathIndexEnabled) {
      return;
    }

    long started = System.nanoTime();
    Path index = getIndexFileFile();
    DataOutput writer = null;
    boolean isOk = false;
    try {
      writer = new UnsyncDataOutputStream(new BufferedOutputStream(Files.newOutputStream(index), 32_000));
      DataInputOutputUtilRt.writeINT(writer, ourVersion);
      data.save(writer);
      isOk = true;
    }
    catch (IOException ignore) {
    }
    finally {
      if (writer != null) {
        try {
          ((OutputStream)writer).close();
        }
        catch (IOException ignore) {
        }
      }
      if (!isOk) {
        try {
          Files.deleteIfExists(index);
        }
        catch (IOException ignore) {
        }
      }
      totalSaving.addAndGet(System.nanoTime() - started);
    }
  }

  private Path getIndexFileFile() {
    return rootDir.resolve("classpath.index");
  }

  @Override
  public @NotNull ClasspathCache.LoaderData buildData() {
    ClasspathCache.LoaderData loaderData = tryReadFromIndex();

    int nsMsFactor = 1000000;
    int currentLoaders = totalLoaders.incrementAndGet();
    long currentScanningTime;
    if (loaderData == null) {
      long started = System.nanoTime();

      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      buildPackageCache(rootDir, loaderDataBuilder);
      loaderData = loaderDataBuilder.build();
      long doneNanos = System.nanoTime() - started;
      currentScanningTime = totalScanning.addAndGet(doneNanos);
      if (doFsActivityLogging) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Scanned: " + rootDir + " for " + (doneNanos / nsMsFactor) + "ms");
      }
      trySaveToIndex(loaderData);
    }
    else {
      currentScanningTime = totalScanning.get();
    }

    if (doFsActivityLogging) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Scanning: " + (currentScanningTime / nsMsFactor) + "ms, saving: " + (totalSaving.get() / nsMsFactor) +
                         "ms, loading:" + (totalReading.get() / nsMsFactor) + "ms for " + currentLoaders + " loaders");
    }

    return loaderData;
  }

  private static final class FileResource extends Resource {
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
    return "FileLoader [" + rootDir + "]";
  }

  @SuppressWarnings({"NonSynchronizedMethodOverridesSynchronizedMethod", "SpellCheckingInspection"})
  private static final class UnsyncDataOutputStream extends DataOutputStream {
    UnsyncDataOutputStream(@NotNull OutputStream out) {
      super(out);
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
    }
  }
}
