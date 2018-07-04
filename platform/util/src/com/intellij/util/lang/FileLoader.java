/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class FileLoader extends Loader {
  private final File myRootDir;
  private final String myRootDirAbsolutePath;
  private final ClassPath myConfiguration;

  FileLoader(URL url, int index, ClassPath configuration) {
    super(url, index);
    myRootDir = new File(FileUtil.unquote(url.getFile()));
    myRootDirAbsolutePath = myRootDir.getAbsolutePath();
    myConfiguration = configuration;
  }

  private void buildPackageCache(final File dir, ClasspathCache.LoaderData loaderData) {
    loaderData.addResourceEntry(getRelativeResourcePath(dir));

    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }

    boolean containsClasses = false;
    for (File file : files) {
      final boolean isClass = file.getPath().endsWith(UrlClassLoader.CLASS_EXTENSION);
      if (isClass) {
        if (!containsClasses) {
          loaderData.addResourceEntry(getRelativeResourcePath(file));
          containsClasses = true;
        }
        loaderData.addNameEntry(file.getName());
      }
      else {
        loaderData.addNameEntry(file.getName());
        buildPackageCache(file, loaderData);
      }
    }
  }

  private String getRelativeResourcePath(final File file) {
    return getRelativeResourcePath(file.getAbsolutePath());
  }

  private String getRelativeResourcePath(final String absFilePath) {
    String relativePath = absFilePath.substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    relativePath = StringUtil.trimStart(relativePath, "/");
    return relativePath;
  }

  @Override
  @Nullable
  Resource getResource(final String name) {
    try {
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

  private static final AtomicInteger totalLoaders = new AtomicInteger();
  private static final AtomicLong totalScanning = new AtomicLong();
  private static final AtomicLong totalSaving = new AtomicLong();
  private static final AtomicLong totalReading = new AtomicLong();

  private static final Boolean doFsActivityLogging = false;

  private ClasspathCache.LoaderData tryReadFromIndex() {
    if (!myConfiguration.myCanHavePersistentIndex) return null;
    long started = System.nanoTime();
    ClasspathCache.LoaderData loaderData = new ClasspathCache.LoaderData();
    File index = getIndexFileFile();

    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(index));
      readList(reader, loaderData.getResourcePaths());
      readList(reader, loaderData.getNames());

      return loaderData;
    } catch (Exception ex) {
      if (!(ex instanceof FileNotFoundException)) index.delete();
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ignore) {}
      }
      totalReading.addAndGet(System.nanoTime() - started);
    }

    return null;
  }

  private static void readList(BufferedReader reader, List<String> paths) throws IOException {
    String line = reader.readLine();
    int numberOfElements = Integer.parseInt(line);
    for(int i = 0; i < numberOfElements; ++i) paths.add(reader.readLine());
  }

  private void trySaveToIndex(ClasspathCache.LoaderData data) {
    if (!myConfiguration.myCanHavePersistentIndex) return;
    long started = System.nanoTime();
    File index = getIndexFileFile();
    BufferedWriter writer = null;

    try {
      writer = new BufferedWriter(new FileWriter(index));
      writeList(writer, data.getResourcePaths());
      writeList(writer, data.getNames());
    } catch (IOException ex) {
      index.delete();
    }
    finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ignore) {}
      }
      totalSaving.addAndGet(System.nanoTime() - started);
    }
  }

  private static void writeList(BufferedWriter writer, List<String> paths) throws IOException {
    writer.append(Integer.toString(paths.size())).append('\n');
    for(String s: paths) writer.append(s).append('\n');
  }

  @NotNull
  private File getIndexFileFile() {
    return new File(myRootDir, "classpath.index");
  }

  @NotNull
  @Override
  public ClasspathCache.LoaderData buildData() throws IOException {
    ClasspathCache.LoaderData fromIndex = tryReadFromIndex();
    final ClasspathCache.LoaderData loaderData = fromIndex != null ? fromIndex : new ClasspathCache.LoaderData();

    final int nsMsFactor = 1000000;
    int currentLoaders = totalLoaders.incrementAndGet();
    long currentScanning;
    if (fromIndex == null) {
      long started = System.nanoTime();
/*    // todo code below uses java 7 api, uncomment once we are done with Java 6
      if (SystemInfo.isJavaVersionAtLeast("1.7") && !SystemProperties.getBooleanProperty("idea.no.nio.class.scanning", false) && false) {
        final Path start = Paths.get(myRootDir.getPath());
        Files.walkFileTree(start, new FileVisitor<Path>() {
          final Stack<Boolean> containsClasses = new Stack<Boolean>();
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            containsClasses.push(Boolean.FALSE);
            if (dir != start) loaderData.addNameEntry(dir.getFileName().toString());
            loaderData.addResourceEntry(getRelativeResourcePath(dir.toAbsolutePath().toString()));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
            String fileName = file.getFileName().toString();
            final boolean isClass = fileName.endsWith(UrlClassLoader.CLASS_EXTENSION);
            if (isClass) {
              Boolean dirContainClasses = containsClasses.peek();
              if (dirContainClasses == Boolean.FALSE) {
                loaderData.addResourceEntry(getRelativeResourcePath(file.toAbsolutePath().toString()));
                containsClasses.set(containsClasses.size() - 1, Boolean.TRUE);
              }
              loaderData.addNameEntry(fileName);
            }
            else {
              loaderData.addNameEntry(fileName);
              loaderData.addResourceEntry(getRelativeResourcePath(file.toAbsolutePath().toString()));
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            containsClasses.pop();
            return FileVisitResult.CONTINUE;
          }
        });
      } else {  */
        buildPackageCache(myRootDir, loaderData);
      /* } */
      final long doneNanos = System.nanoTime() - started;
      currentScanning = totalScanning.addAndGet(doneNanos);
      if (doFsActivityLogging) {
        System.out.println("Scanned: " + myRootDirAbsolutePath + " for " + (doneNanos / nsMsFactor) + "ms");
      }
      trySaveToIndex(loaderData);
    } else {
      currentScanning = totalScanning.get();
    }

    loaderData.addResourceEntry("foo.class");
    loaderData.addResourceEntry("bar.properties");

    if (doFsActivityLogging) {
      System.out.println("Scanning: " + (currentScanning / nsMsFactor) + "ms, saving: " + (totalSaving.get() / nsMsFactor) +
                         "ms, loading:" + (totalReading.get() / nsMsFactor) + "ms for " + currentLoaders + " loaders");
    }

    return loaderData;
  }

  private static class MyResource extends Resource {
    private final URL myUrl;
    private final File myFile;

    public MyResource(URL url, File file) {
      myUrl = url;
      myFile = file;
    }

    @Override
    public URL getURL() {
      return myUrl;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(myFile));
    }

    @Override
    public byte[] getBytes() throws IOException {
      return FileUtil.loadFileBytes(myFile);
    }
  }

  @Override
  public String toString() {
    return "FileLoader [" + myRootDir + "]";
  }
}
