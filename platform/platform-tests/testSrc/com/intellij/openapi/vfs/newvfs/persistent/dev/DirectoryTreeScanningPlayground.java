// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * Not a test, but set of primitive benchmarks to estimate FS scanning time of different kinds
 */
@Ignore("Not a test, benchmark/playground")
public class DirectoryTreeScanningPlayground {
  public static final File ROOT = new File("/Users/cheremin.ruslan/Documents/Development/intellij/");

  private static final ForkJoinPool FJP_X2 = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2 + 1);

  //RC: best results so far are scanWithNIODirectoryStreamAsyncCounting and scanWithListFilesAsyncCounting
  //    they are in 5% from each other, and I think it is not a significant difference given quite pure
  //    benchmark quality.
  //
  //    Interesting facts: seems like larger FJP pool (FJP_X2) performs slightly worse than commonPool.
  //    This surprises me, since for IO-bound task I'd expect additional threads to help pool keep
  //    doing work while some threads are blocked on IO -- but somehow this happens to be not true?
  //
  //    Also interesting: newer NIO newDirectoryStream() performs the same as File.listFiles() -- again,
  //    I'd expect new API to perform slightly better, but -- at least on this pure-man benchmarks --
  //    results are indistinguishable. I'd expect DirectoryStream to use less memory on a large directories
  //    though, so probably still worth to use it?


  @Test
  public void scanWithListFilesNoop() throws IOException {
    final long startedAtNs = System.nanoTime();
    scanDirectoryWithListFiles(ROOT,
                               dir -> {
                                 return true;
                               },
                               file -> {
                               }
    );
    final long finishedAtNs = System.nanoTime();
    System.out.println(ROOT + ": " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithListFilesCounting() throws IOException {
    final long startedAtNs = System.nanoTime();
    final int[] counterHolder = {0};
    scanDirectoryWithListFiles(ROOT,
                               dir -> {
                                 counterHolder[0]++;
                                 return true;
                               },
                               file -> {
                                 counterHolder[0]++;
                               }
    );
    final long finishedAtNs = System.nanoTime();
    System.out.println(ROOT + ": " + counterHolder[0] + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithListFilesAsyncCounting() throws IOException {
    final long startedAtNs = System.nanoTime();
    final AtomicInteger counterHolder = new AtomicInteger(0);
    scanDirectoryWithListFilesFJP(
      ROOT,
      dir -> {
        counterHolder.incrementAndGet();
        return true;
      },
      file -> {
        counterHolder.incrementAndGet();
      }
    );
    final long finishedAtNs = System.nanoTime();
    System.out.println(ROOT + ": " + counterHolder + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithNIODirectoryStreamAsyncNoop() throws IOException {
    final long startedAtNs = System.nanoTime();

    scanDirectoryWithDirectoryStreamFJP(
      ROOT.toPath(),
      dir -> {
        //counterHolder.incrementAndGet();
        return true;
      },
      file -> {
        //counterHolder.incrementAndGet();
      }
    );
    System.out.println(ROOT + ": " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithNIODirectoryStreamAsyncCounting() throws IOException {
    final long startedAtNs = System.nanoTime();

    final AtomicInteger counterHolder = new AtomicInteger(0);
    //FJP_X2.submit( () -> ... ).join()
    scanDirectoryWithDirectoryStreamFJP(
      ROOT.toPath(),
      dir -> {
        counterHolder.incrementAndGet();
        return true;
      },
      file -> {
        counterHolder.incrementAndGet();
      }
    );
    System.out.println(ROOT + ": " + counterHolder + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithNIODirectoryStreamAsyncCounting_NO_FOLLOW_LINK() throws IOException {
    final long startedAtNs = System.nanoTime();

    final AtomicInteger counterHolder = new AtomicInteger(0);
    scanDirectoryWithDirectoryStreamFJP_NOFOLLOW_LINKS(
      ROOT.toPath(),
      dir -> {
        counterHolder.incrementAndGet();
        return true;
      },
      file -> {
        counterHolder.incrementAndGet();
      }
    );
    System.out.println(ROOT + ": " + counterHolder + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }


  @Test
  public void scanWithNIOWalkTreeNoop() throws IOException {
    final long startedAtNs = System.nanoTime();
    Files.walkFileTree(ROOT.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return super.preVisitDirectory(dir, attrs);
      }

      @Override
      public FileVisitResult visitFile(final Path file,
                                       final BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }
    });
    System.out.println(ROOT + ": " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  public void scanWithNIOWalkTreeCounting() throws IOException {
    final long startedAtNs = System.nanoTime();
    final int[] counterHolder = {0};

    Files.walkFileTree(ROOT.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(final Path file,
                                       final BasicFileAttributes attrs) throws IOException {
        counterHolder[0]++;
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(final Path dir,
                                               final BasicFileAttributes attrs) throws IOException {
        counterHolder[0]++;
        return super.preVisitDirectory(dir, attrs);
      }
    });
    System.out.println(ROOT + ": " + counterHolder[0] + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  @Test
  @Ignore("Not finished yet")
  public void scanWithNIOWalkTreeCreatingFSRecordsEntries() throws IOException {
    final long startedAtNs = System.nanoTime();

    Files.walkFileTree(ROOT.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(final Path file,
                                       final BasicFileAttributes attrs) throws IOException {
        final int fileId = FSRecords.createRecord();
        // other methods are package-local
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(final Path dir,
                                               final BasicFileAttributes attrs) throws IOException {
        final int fileId = FSRecords.createRecord();
        return super.preVisitDirectory(dir, attrs);
      }
    });
    //System.out.println(ROOT + ": " + counterHolder[0] + " files/dirs scanned, " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }



  /* =========================== infrastructure =========================================== */

  private void scanDirectoryWithListFiles(final File directory,
                                          final Predicate<File> directoryConsumer,
                                          final Consumer<File> fileConsumer) throws IOException {
    if (!directoryConsumer.test(directory)) {
      throw new AssertionError("Code bug: " + directory + " must be a directory");
    }
    for (File child : directory.listFiles()) {
      if (child.isDirectory()) {
        scanDirectoryWithListFiles(child, directoryConsumer, fileConsumer);
      }
      else {
        fileConsumer.accept(child);
      }
    }
  }

  private void scanDirectoryWithListFilesFJP(final File directory,
                                             final Predicate<File> directoryConsumer,
                                             final Consumer<File> fileConsumer) {
    if (!directoryConsumer.test(directory)) {
      throw new AssertionError("Code bug: " + directory + " must be a directory");
    }
    final List<RecursiveAction> subDirectoryActions = new ArrayList<>();
    final List<File> files = new ArrayList<>();
    for (File child : directory.listFiles()) {
      if (child.isDirectory()) {
        final RecursiveAction action = new RecursiveAction() {
          @Override
          protected void compute() {
            scanDirectoryWithListFilesFJP(child, directoryConsumer, fileConsumer);
          }
        };
        action.fork();
        subDirectoryActions.add(action);
      }
      else {
        files.add(child);
      }
    }

    for (File file : files) {
      fileConsumer.accept(file);
    }
    for (RecursiveAction action : subDirectoryActions) {
      action.join();
    }
  }

  private void scanDirectoryWithDirectoryStreamFJP(final Path directory,
                                                   final Predicate<Path> directoryConsumer,
                                                   final Consumer<Path> fileConsumer) {
    if (!directoryConsumer.test(directory)) {
      throw new AssertionError("Code bug: " + directory + " must be a directory");
    }
    final List<RecursiveAction> subDirectoryActions = new ArrayList<>();
    final List<Path> plainFilesPaths = new ArrayList<>();
    try (final DirectoryStream<Path> paths = Files.newDirectoryStream(directory)) {
      for (Path child : paths) {
        if (Files.isDirectory(child)) {
          final RecursiveAction action = new RecursiveAction() {
            @Override
            protected void compute() {
              scanDirectoryWithDirectoryStreamFJP(child, directoryConsumer, fileConsumer);
            }
          };
          action.fork();
          subDirectoryActions.add(action);
        }
        else {
          plainFilesPaths.add(child);
        }
      }
    }
    catch (IOException e) {
      ExceptionUtil.rethrow(e);
    }

    for (Path filePath : plainFilesPaths) {
      fileConsumer.accept(filePath);
    }
    for (RecursiveAction action : subDirectoryActions) {
      action.join();
    }
  }

  private void scanDirectoryWithDirectoryStreamFJP_NOFOLLOW_LINKS(final Path directory,
                                                                  final Predicate<Path> directoryConsumer,
                                                                  final Consumer<Path> fileConsumer) {
    if (!directoryConsumer.test(directory)) {
      throw new AssertionError("Code bug: " + directory + " must be a directory");
    }
    final List<RecursiveAction> subDirectoryActions = new ArrayList<>();
    final List<Path> plainFilesPaths = new ArrayList<>();
    try (final DirectoryStream<Path> paths = Files.newDirectoryStream(directory)) {
      for (Path child : paths) {
        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
          final RecursiveAction walkSubdir = new RecursiveAction() {
            @Override
            protected void compute() {
              scanDirectoryWithDirectoryStreamFJP_NOFOLLOW_LINKS(child, directoryConsumer, fileConsumer);
            }
          };
          walkSubdir.fork();
          subDirectoryActions.add(walkSubdir);
        }
        else {
          plainFilesPaths.add(child);
        }
      }
    }
    catch (IOException e) {
      ExceptionUtil.rethrow(e);
    }

    for (Path filePath : plainFilesPaths) {
      fileConsumer.accept(filePath);
    }
    for (RecursiveAction action : subDirectoryActions) {
      action.join();
    }
  }


  //============================= TODO investigate it =========

  public static final int THREADS = Runtime.getRuntime().availableProcessors() * 2;

  public static void main(String[] args) {
    final Path path = ROOT.toPath();

    System.out.println("Statistic for " + path);

    var forked = walk(path, p -> {
      Queue<Path> paths = new ConcurrentLinkedQueue<>();
      final ForkJoinPool fjp = new ForkJoinPool(THREADS);
      fjp.invoke(new FileWalker(path, paths::add));
      return paths;
    }, "ForkJoinPool");

    var executed = walk(path, p -> {
      Queue<Path> paths = new ConcurrentLinkedQueue<>();
      ExecutorFileWalker walker = new ExecutorFileWalker(p);
      walker.start(paths::add);
      return paths;
    }, "Exec. Service");

    var streamed = walk(path, p -> Files.walk(p).parallel().collect(Collectors.toList()), "Streams Par.");
  }

  private static <T extends Collection<Path>> T walk(Path path, Traverse<T> traverse, String name) {
    long start = System.currentTimeMillis();
    T result;
    try {
      result = traverse.walk(path);
    }
    catch (IOException | ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    long stop = System.currentTimeMillis();
    System.out.println(name + ":\tfound " + result.size() + " paths in " + (stop - start) + " ms");
    return result;
  }

  private static class ExecutorFileWalker {
    private final Path dir;
    private final ExecutorService service = Executors.newFixedThreadPool(THREADS);
    private final AtomicInteger lock = new AtomicInteger(0);

    private ExecutorFileWalker(Path dir) {
      this.dir = dir;
    }

    public void start(Consumer<Path> consumer) {
      submit(() -> run(dir, consumer));
      while (lock.get() != 0) {
      }
      service.shutdown();
    }

    private void run(Path dir, Consumer<Path> consumer) {
      consumer.accept(dir);
      forEachSubdir(dir, p -> submit(() -> run(p, consumer)));
    }

    private void submit(Runnable runnable) {
      lock.incrementAndGet();
      service.submit(() -> {
        runnable.run();
        lock.decrementAndGet();
      });
    }
  }

  private static class FileWalker extends RecursiveAction {

    private final Path path;
    private final Consumer<Path> consumer;

    FileWalker(Path path, Consumer<Path> consumer) {
      Objects.requireNonNull(path);
      this.path = path;
      this.consumer = consumer;
    }

    @Override
    protected void compute() {
      consumer.accept(path);
      List<FileWalker> tasks = new LinkedList<>();
      forEachSubdir(path, p -> tasks.add(new FileWalker(p, consumer)));
      if (!tasks.isEmpty()) invokeAll(tasks);
    }
  }

  private static void forEachSubdir(final Path dir,
                                    final Consumer<Path> children) {
    if (Files.isRegularFile(dir, NOFOLLOW_LINKS)) return;
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir)) {
      paths.forEach(children);
    }
    catch (IOException ignore) {
    }
  }

  @FunctionalInterface
  private interface Traverse<T extends Collection<Path>> {
    T walk(Path path) throws IOException, ExecutionException, InterruptedException;
  }
}