// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class FileLoader extends Loader {
  private final File myRootDir;
  private final String myRootDirAbsolutePath;
  private final ClassPath myConfiguration;

  FileLoader(@NotNull URL url, @NotNull ClassPath configuration) throws IOException {
    super(url);
    try {
      myRootDir = new File(url.toURI());
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
    myRootDirAbsolutePath = myRootDir.getAbsolutePath();
    myConfiguration = configuration;
  }

  private void buildPackageCache(@NotNull File dir, @NotNull ClasspathCache.LoaderDataBuilder context) {
    context.addResourcePackageFromName(getRelativeResourcePath(dir));

    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }

    boolean containsClasses = false;

    for (File file : files) {
      final boolean isClass = file.getPath().endsWith(UrlClassLoader.CLASS_EXTENSION);
      if (isClass) {
        if (!containsClasses) {
          context.addClassPackageFromName(getRelativeResourcePath(file));
          containsClasses = true;
        }
        context.addPossiblyDuplicateNameEntry(file.getName());
      }
      else {
        context.addPossiblyDuplicateNameEntry(file.getName());
        buildPackageCache(file, context);
      }
    }
  }

  @NotNull
  private String getRelativeResourcePath(@NotNull File file) {
    return getRelativeResourcePath(file.getAbsolutePath());
  }

  @NotNull
  private String getRelativeResourcePath(@NotNull String absFilePath) {
    String relativePath = absFilePath.substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    relativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    return relativePath;
  }

  private static class DirEntry {
    static final int[] empty = new int[0];
    volatile int[] childrenNameHashes;

    volatile DirEntry[] childrenDirectories;
    final int nameHash;
    @NotNull final String name;

    DirEntry(int nameHash, @NotNull String name) {
      this.nameHash = nameHash;
      this.name = name;
    }
  }

  private final DirEntry root = new DirEntry(0, "");

  @Override
  @Nullable
  Resource getResource(@NotNull final String name) {
    try {
      if (myConfiguration.myLazyClassloadingCaches) {
        DirEntry lastEntry = root;
        int prevIndex = 0;   // 0 .. prevIndex is package
        int nextIndex = name.indexOf('/', prevIndex);

        while (true) {
          int nameEnd = nextIndex == -1 ? name.length() : nextIndex; // prevIndex, nameEnd is package or class name
          int nameHash = StringUtilRt.stringHashCodeInsensitive(name, prevIndex, nameEnd);
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

      URL url = new URL(getBaseURL(), name);
      if (!url.getFile().startsWith(getBaseURL().getFile())) return null;
      File file = new File(myRootDir, name.replace('/', File.separatorChar));
      if (file.exists()) {
        return new MyResource(url, file);
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  @NotNull
  private static DirEntry findOrCreateNextDirEntry(@NotNull DirEntry lastEntry, @NotNull String name,
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
      lastEntry.childrenDirectories = newChildrenDirectories; // volatile write with new copy of data
    }

    lastEntry = nextEntry;
    return lastEntry;
  }

  private boolean nameHashIsPresentInChildren(@NotNull DirEntry lastEntry, @NotNull String name, int prevIndex, int nameHash) {
    int[] childrenNameHashes = lastEntry.childrenNameHashes; // volatile read

    if (childrenNameHashes == null) {
      String[] list = (prevIndex != 0 ? new File(myRootDir, name.substring(0, prevIndex)) : myRootDir).list();

      if (list != null) {
        childrenNameHashes = new int[list.length];
        for (int i = 0; i < list.length; ++i) {
          childrenNameHashes[i] = StringUtilRt.stringHashCodeInsensitive(list[i], 0, list[i].length());
        }
      }
      else {
        childrenNameHashes = DirEntry.empty;
      }
      lastEntry.childrenNameHashes = childrenNameHashes; // volatile write
    }

    for (int childNameHash : childrenNameHashes) {
      if (childNameHash == nameHash) {
        return true;
      }
    }
    return false;
  }

  private static final AtomicInteger totalLoaders = new AtomicInteger();
  private static final AtomicLong totalScanning = new AtomicLong();
  private static final AtomicLong totalSaving = new AtomicLong();
  private static final AtomicLong totalReading = new AtomicLong();

  private static final Boolean doFsActivityLogging = false;
  private static final int ourVersion = 1;

  private ClasspathCache.LoaderData tryReadFromIndex() {
    if (!myConfiguration.myCanHavePersistentIndex) return null;
    long started = System.nanoTime();

    File index = getIndexFileFile();

    DataInputStream reader = null;
    boolean isOk = false;

    try {
      reader = new DataInputStream(new BufferedInputStream(new FileInputStream(index)));
      if (DataInputOutputUtilRt.readINT(reader) == ourVersion) {
        ClasspathCache.LoaderData loaderData = new ClasspathCache.LoaderData(reader);
        isOk = true;
        return loaderData;
      }
    }
    catch (FileNotFoundException ex) {
      isOk = true;
    }
    catch (IOException ignore) {
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException ignore) {
        }
      }
      if (!isOk) {
        index.delete();
      }
      totalReading.addAndGet(System.nanoTime() - started);
    }

    return null;
  }

  private void trySaveToIndex(@NotNull ClasspathCache.LoaderData data) {
    if (!myConfiguration.myCanHavePersistentIndex) return;
    long started = System.nanoTime();
    File index = getIndexFileFile();
    DataOutput writer = null;
    boolean isOk = false;

    try {
      writer = new UnsyncDataOutputStream(new BufferedOutputStream(new FileOutputStream(index)));
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
      if (!isOk) index.delete();
      totalSaving.addAndGet(System.nanoTime() - started);
    }
  }

  @NotNull
  private File getIndexFileFile() {
    return new File(myRootDir, "classpath.index");
  }

  @NotNull
  @Override
  public ClasspathCache.LoaderData buildData() {
    ClasspathCache.LoaderData loaderData = tryReadFromIndex();

    final int nsMsFactor = 1000000;
    int currentLoaders = totalLoaders.incrementAndGet();
    long currentScanningTime;
    if (loaderData == null) {
      long started = System.nanoTime();
      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      buildPackageCache(myRootDir, loaderDataBuilder);
      loaderData = loaderDataBuilder.build();
      final long doneNanos = System.nanoTime() - started;
      currentScanningTime = totalScanning.addAndGet(doneNanos);
      if (doFsActivityLogging) {
        System.out.println("Scanned: " + myRootDirAbsolutePath + " for " + (doneNanos / nsMsFactor) + "ms");
      }
      trySaveToIndex(loaderData);
    }
    else {
      currentScanningTime = totalScanning.get();
    }

    if (doFsActivityLogging) {
      System.out.println("Scanning: " + (currentScanningTime / nsMsFactor) + "ms, saving: " + (totalSaving.get() / nsMsFactor) +
                         "ms, loading:" + (totalReading.get() / nsMsFactor) + "ms for " + currentLoaders + " loaders");
    }

    return loaderData;
  }

  private static final class MyResource extends Resource {
    private final URL myUrl;
    private final File myFile;

    MyResource(@NotNull URL url, @NotNull File file) {
      myUrl = url;
      myFile = file;
    }

    @NotNull
    @Override
    public URL getURL() {
      return myUrl;
    }

    @NotNull
    @Override
    public InputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(myFile));
    }

    @NotNull
    @Override
    public byte[] getBytes() throws IOException {
      InputStream stream = getInputStream();
      try {
        return FileUtilRt.loadBytes(stream, (int)myFile.length());
      }
      finally {
        stream.close();
      }
    }
  }

  @Override
  public String toString() {
    return "FileLoader [" + myRootDir + "]";
  }

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
