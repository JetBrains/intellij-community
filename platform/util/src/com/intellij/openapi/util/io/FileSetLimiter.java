// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Manages a set of log-like files: generates new filename, keeps total number of files limited.<br/>
 * Usage examples:
 * <br/>
 * Create new file, while keeping total files number limited:
 * <pre>
 * final Path newlyCreatedFile = FileSetLimiter.inDirectory(dir)
 *                   .withBaseNameAndDateFormatSuffix("my-file.log", "yyyy-MM-dd-HH-mm-ss")
 *                   .withMaxFilesToKeep(10)
 *                   .createNewFile(); // _creates_ new file,
 *                                     // AND if there are > 10 'my-file.[yyyy-MM-dd-HH-mm-ss].log' files
 *                                     //     -> removes few oldest ones
 * </pre>
 * <p>
 * Only housekeeping, do not create new files:
 * <pre>
 * FileSetLimiter.inDirectory(dir)
 *               .withBaseNameAndDateFormatSuffix("my-file.log", "yyyy-MM-dd-HH-mm-ss")
 *               .removeOlderFilesBut(10); // if there are > 10 'my-file.[yyyy-MM-dd-HH-mm-ss].log' files
 *                                         // -> removes few oldest ones
 * </pre>
 */
@ApiStatus.Internal
public final class FileSetLimiter {
  private static final Logger LOG = Logger.getInstance(FileSetLimiter.class);

  public static final int DEFAULT_FILES_TO_KEEP = 10;
  public static final String DEFAULT_DATETIME_FORMAT = "{0, date, yyyy-MM-dd-HH-mm-ss}";

  public static final Consumer<Collection<? extends Path>> DELETE_IMMEDIATELY = paths -> {
    for (Path path : paths) {
      final File file = path.toFile();
      if (!FileUtilRt.deleteFile(file)) {
        file.deleteOnExit();
        LOG.info("Can't delete " + path + " (scheduled for delete on exit)");
      }
    }
  };

  public static final Consumer<Collection<? extends Path>> DELETE_ON_JVM_EXIT = paths -> {
    for (Path path : paths) {
      path.toFile().deleteOnExit();
    }
  };

  public static final Consumer<Collection<? extends Path>> DELETE_ASYNC = paths -> {
    if (!paths.isEmpty()) {
      ExecutorsKt.asExecutor(Dispatchers.getIO()).execute(() -> {
        final Thread currentThread = Thread.currentThread();
        final int priority = currentThread.getPriority();
        currentThread.setPriority(Thread.MIN_PRIORITY);
        try {
          DELETE_IMMEDIATELY.accept(paths);
        }
        finally {
          currentThread.setPriority(priority);
        }
      });
    }
  };

  private final @NotNull Path directory;
  /** E.g. 'my-log-file.{0,date,yyyy-MM-dd-HH-mm-ss}.log' */
  private final @NotNull MessageFormat fileNameFormat;

  private final int maxFilesToKeep;
  /** Strategy for older files deletion (plain .delete() could be too slow in some cases) */
  private final Consumer<Collection<? extends Path>> filesDeleter;

  private FileSetLimiter(final @NotNull Path directory,
                         final @NotNull MessageFormat fileNameFormat,
                         final int maxFilesToKeep,
                         final @NotNull Consumer<Collection<? extends Path>> deleter) {
    filesDeleter = deleter;
    if (maxFilesToKeep <= 1) {
      throw new IllegalArgumentException("maxFilesToKeep(=" + maxFilesToKeep + ") should be >=1");
    }
    this.directory = directory;
    this.fileNameFormat = fileNameFormat;
    this.maxFilesToKeep = maxFilesToKeep;
  }

  public static FileSetLimiter inDirectory(final Path directory) {
    return new FileSetLimiter(
      directory,
      new MessageFormat(DEFAULT_DATETIME_FORMAT),
      DEFAULT_FILES_TO_KEEP,
      DELETE_IMMEDIATELY
    );
  }

  public FileSetLimiter withFileNameFormat(final @NotNull String fileNameFormat) {
    return withFileNameFormat(new MessageFormat(fileNameFormat));
  }

  public FileSetLimiter withFileNameFormat(final @NotNull MessageFormat fileNameFormat) {
    return new FileSetLimiter(directory, fileNameFormat, maxFilesToKeep, filesDeleter);
  }

  /** (myfile.csv, 'yyyy-MM-dd-HH-mm-ss') -> 'myfile.{0,date,'yyyy-MM-dd-HH-mm-ss'}.csv' */
  public FileSetLimiter withBaseNameAndDateFormatSuffix(final @NotNull String baseFileName,
                                                        final @NotNull String suffixDateFormat) {
    return withFileNameFormat(fileNameFormatFromBaseFileNameAndDateFormat(baseFileName, suffixDateFormat));
  }

  /**
   * Does not remove any actual files -- just configures new {@link FileSetLimiter} instance
   */
  public FileSetLimiter withMaxFilesToKeep(final int maxFilesToKeep) {
    return withMaxFilesToKeep(maxFilesToKeep, filesDeleter);
  }

  public FileSetLimiter withMaxFilesToKeep(final int maxFilesToKeep,
                                           final Consumer<Collection<? extends Path>> excessiveFilesDeleter) {
    return new FileSetLimiter(directory, fileNameFormat, maxFilesToKeep, excessiveFilesDeleter);
  }

  /**
   * Configures new {@link FileSetLimiter} instance AND actually looks up and removes excessive files
   * in the directory.
   */
  public FileSetLimiter removeOldFilesBut(final int maxFilesToKeep) throws IOException {
    return withMaxFilesToKeep(maxFilesToKeep, filesDeleter)
      .removeOlderFiles();
  }

  /**
   * Configures new {@link FileSetLimiter} instance AND actually looks up and removes excessive files
   * in the directory.
   */
  public FileSetLimiter removeOldFilesBut(final int maxFilesToKeep,
                                          final Consumer<Collection<? extends Path>> excessiveFilesDeleter) throws IOException {
    return withMaxFilesToKeep(maxFilesToKeep, excessiveFilesDeleter)
      .removeOlderFiles();
  }

  /**
   * Checks is there too many files matched with the pattern, and remove excessive files.
   * BEWARE: depending on {@link #filesDeleter} configured, actual file deletion could be delayed
   * for quite a while (e.g. until JVM exit). If immediate effect required, use {@link #DELETE_IMMEDIATELY}
   */
  public FileSetLimiter removeOlderFiles() throws IOException {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return this; //no house to keep
    }
    // find files matching fileNameFormat, extract dates of creation, and remove the oldest files
    try (final Stream<Path> children = Files.list(directory)) {
      final Comparator<Pair<Path, Date>> byDateOfCreation = comparing(pair -> pair.second);
      final List<Path> excessiveFilesToRemove = children.map(this::tryParsePath)
        .filter(pair -> pair.second != null)
        .sorted(byDateOfCreation.reversed())
        .skip(maxFilesToKeep)
        .map(pair -> pair.first)
        .collect(toList());

      filesDeleter.accept(excessiveFilesToRemove);
    }
    return this;
  }

  /**
   * Creates new file, clean older files, if > maxFilesToKeep of them.
   * <br/>
   * <b>NOTE</b>: newly created file <b>is counted</b> in cleanup older files -- i.e. if maxFilesToKeep=10,
   * and there are already 10 files in a directory, and createNewFile() is called -> 1 oldest file will be
   * removed, since after new file created there are 11 files in a directory.
   */
  public Path createNewFile() throws IOException {
    return createNewFile(Clock.systemDefaultZone());
  }

  public Path createNewFile(final @NotNull Clock clock) throws IOException {
    final int maxTries = 1024;
    try {
      final Path basePath = generatePath(clock);
      Path candidatePath = basePath;
      for (int tryNo = 0; tryNo < maxTries; tryNo++) {
        try {
          if (Files.exists(candidatePath)) {
            final String numeratedFileName = basePath.getFileName() + "." + tryNo;
            candidatePath = basePath.resolveSibling(numeratedFileName);
          }
          final Path createdPath = Files.createFile(candidatePath);
          return createdPath;
        }
        catch (FileAlreadyExistsException e) {
          //retry with new candidate
        }
      }
      throw new IOException("Can't create new file like " + basePath + ".X in X=[0.." + maxTries + ") attempts");
    }
    finally {
      removeOlderFiles();
    }
  }

  /**
   * Clean older files, generates path, but doesn't create the file.
   * <br/>
   * <b>NOTE</b>: since newly generated path is not 'materialized', it is <b>not counted</b> in cleanup
   * older files (contrary to {@link #createNewFile()} behavior) -- i.e. if maxFilesToKeep=10, and there
   * are already 10 files in a directory, and generatePath() is called -> nothing will be removed,
   * since new path is not yet exist as a file, so there are still only 10 files in a directory
   * which is <= maxFilesToKeep.
   */
  public Path generatePath() throws IOException {
    return generatePath(Clock.systemDefaultZone());
  }

  public Path generatePath(final @NotNull Clock clock) throws IOException {
    if (!Files.isDirectory(directory)) {
      //createDirectories() _does_ throw FileAlreadyExistsException if path is a _symlink_ to a directory,
      // not a directory itself (JDK-8130464). Check !isDirectory() above should work around that case: if
      // directory is a symlink to an existing dir -> we just skip createDirectories() altogether.
      Files.createDirectories(directory);
    }

    removeOlderFiles();

    final String fileName = fileNameFormat.format(new Object[]{clock.millis()});
    final Path newFile = directory.resolve(fileName);
    return newFile;
  }

  public List<Path> listExistentFiles() throws IOException {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return Collections.emptyList();
    }
    // find files matching fileNameFormat, extract dates of creation, and remove the oldest files
    try (final Stream<Path> children = Files.list(directory)) {
      return children.map(this::tryParsePath)
        .filter(pair -> pair.second != null)
        .map(pair -> pair.first)
        .collect(toList());
    }
  }

  public Optional<Path> mostRecentFile() throws IOException {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return Optional.empty();
    }
    // find files matching fileNameFormat, extract dates of creation, and remove the oldest files
    final Comparator<Pair<Path, Date>> byDateOfCreation = comparing(pair -> pair.second);
    try (final Stream<Path> children = Files.list(directory)) {
      return children.map(this::tryParsePath)
        .filter(pair -> pair.second != null)
        .max(byDateOfCreation)
        .map(pair -> pair.first);
    }
  }

  /**
   * Splits baseFileName into the name and the extension, and insert dateFormat between them.
   * E.g.: ('my-file.log','yyyy-MM-dd-HH-mm-ss') -> 'my-file.{0,date,yyyy-MM-dd-HH-mm-ss}.log'
   */
  private static @NotNull String fileNameFormatFromBaseFileNameAndDateFormat(final @NotNull String baseFileName,
                                                                    final @NotNull String dateFormat) {
    final String extension = FileUtilRt.getExtension(baseFileName);
    String unzippedExtension = "";
    String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(baseFileName);

    if (extension.equals("gz")) {
      unzippedExtension = FileUtilRt.getExtension(nameWithoutExtension) + ".";
      nameWithoutExtension = FileUtilRt.getNameWithoutExtension(nameWithoutExtension);
    }

    return extension.isEmpty()
           ? nameWithoutExtension + ".{0,date," + dateFormat + "}"
           : nameWithoutExtension + ".{0,date," + dateFormat + "}." + unzippedExtension + extension;
  }

  private @NotNull Pair<Path, Date> tryParsePath(final Path path) {
    //TODO RC: use FileRecord instead of Pair, and parse sub-millisecond suffix also
    final String fileName = directory.relativize(path).toString();
    try {
      final Object[] results = fileNameFormat.parse(fileName);
      final Date fileCreatedAt = (Date)results[0];
      return new Pair<>(path, fileCreatedAt);
    }
    catch (ParseException e) {
      return new Pair<>(path, null);
    }
  }

  /** TODO RC: use it in tryParsePath instead of Pair */
  @SuppressWarnings("unused")
  public static final class FileRecord {
    public final @NotNull Path path;
    public final @Nullable Date date;
    public final int subMillisecondId;

    public FileRecord(final @NotNull Path path,
                      final @Nullable Date date,
                      final int subMillisecondId) {
      this.path = path;
      this.date = date;
      this.subMillisecondId = subMillisecondId;
    }
  }

}
