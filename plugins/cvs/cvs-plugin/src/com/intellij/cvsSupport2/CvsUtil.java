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
package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsInfo;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsRootParser;
import com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider;
import com.intellij.cvsSupport2.util.CvsFileUtil;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entries;
import org.netbeans.lib.cvsclient.admin.EntriesHandler;
import org.netbeans.lib.cvsclient.admin.Entry;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class CvsUtil {

  private static final SyncDateFormat DATE_FORMATTER = new SyncDateFormat(new SimpleDateFormat(Entry.getLastModifiedDateFormatter().toPattern(), Locale.US));

  static {
    //noinspection HardCodedStringLiteral
    DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT+0000"));
  }

  @NonNls public static final String CVS_IGNORE_FILE = ".cvsignore";
  @NonNls public static final String CVS_ROOT_FILE = "Root";
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.CvsUtil");
  @NonNls private static final String REPOSITORY = "Repository";
  @NonNls private static final String TAG = "Tag";
  @NonNls public static final String CVS = "CVS";
  @NonNls public static final String ENTRIES = "Entries";
  @NonNls private static final String CONFLICTS = "Conflicts";
  @NonNls private static final String BASE_REVISIONS_DIR = "BaseRevisions";
  @NonNls public static final String STICKY_DATE_PREFIX = "D";
  @NonNls private static final String TEMPLATE = "Template";
  @NonNls public static final String STICKY_BRANCH_TAG_PREFIX = "T";
  @NonNls public static final String STICKY_NON_BRANCH_TAG_PREFIX = "N";
  @NonNls public static final String HEAD = "HEAD";
  @NonNls public static final String BASE = "Base";
  @NonNls public static final String REVISION_PATTERN = "\\d+(\\.\\d+)*";

  public static void skip(InputStream inputStream, int length) throws IOException {
    int skipped = 0;
    while (skipped < length) {
      skipped += inputStream.skip(length - skipped);
    }
  }

  public static String getModuleName(VirtualFile file) {
    if (file.isDirectory()) {
      return CvsEntriesManager.getInstance().getRepositoryFor(file);
    } else {
      return CvsEntriesManager.getInstance().getRepositoryFor(file.getParent()) + "/" + file.getName();
    }
  }

  public static String getModuleName(FilePath path) {
    if (path.isDirectory()) {
      return CvsEntriesManager.getInstance().getRepositoryFor(path.getVirtualFile());
    } else {
      return CvsEntriesManager.getInstance().getRepositoryFor(path.getVirtualFileParent()) + "/" + path.getName();
    }
  }

  public static boolean fileIsUnderCvsMaybeWithVfs(VirtualFile vFile) {
    try {
      if (Registry.is("cvs.roots.refresh.uses.vfs")) {
        if (vFile.isDirectory()) {
          return directoryIsUnderCVS(vFile);
        }
        return fileIsUnderCvs(getEntryFor(vFile));
      } else {
        return fileIsUnderCvs(vFile);
      }
    }
    catch (Exception e1) {
      return false;
    }
  }

  public static boolean fileIsUnderCvs(VirtualFile vFile) {
    return fileIsUnderCvs(CvsVfsUtil.getFileFor(vFile));
  }

  public static boolean fileIsUnderCvs(File ioFile) {
    try {
      if (ioFile.isDirectory()) {
        return directoryIsUnderCVS(ioFile);
      }
      return fileIsUnderCvs(getEntryFor(ioFile));
    }
    catch (Exception e1) {
      return false;
    }
  }

  private static boolean directoryIsUnderCVS(File directory) {
    if (!getAdminDir(directory).isDirectory()) return false;
    if (!getFileInTheAdminDir(directory, ENTRIES).isFile()) return false;
    if (!getFileInTheAdminDir(directory, CVS_ROOT_FILE).isFile()) return false;
    if (!getFileInTheAdminDir(directory, REPOSITORY).isFile()) return false;
    return true;
  }

  private static boolean directoryIsUnderCVS(VirtualFile vDir) {
    VirtualFile dir = getAdminDir(vDir);
    if (dir == null) return false;

    if (!hasPlainFileInTheAdminDir(dir, ENTRIES)) return false;
    if (!hasPlainFileInTheAdminDir(dir, CVS_ROOT_FILE)) return false;
    if (!hasPlainFileInTheAdminDir(dir, REPOSITORY)) return false;

    return true;
  }

  private static boolean hasPlainFileInTheAdminDir(VirtualFile dir, String filename) {
    VirtualFile child = dir.findChild(filename);
    return child != null && !child.isDirectory();
  }

  public static Entry getEntryFor(@NotNull VirtualFile file) {
    return CvsEntriesManager.getInstance().getEntryFor(file.getParent(), file.getName());
  }

  public static Entry getEntryFor(File ioFile) {
    File parentFile = ioFile.getParentFile();
    if (parentFile == null) return null;
    return CvsEntriesManager.getInstance().getEntryFor(CvsVfsUtil.findFileByIoFile(parentFile),
                                                       ioFile.getName());
  }

  private static boolean fileIsUnderCvs(Entry entry) {
    return entry != null;
  }

  public static boolean filesAreUnderCvs(File[] selectedFiles) {
    return allSatisfy(selectedFiles, fileIsUnderCvsCondition());
  }

  public static boolean filesArentUnderCvs(File[] selectedFiles) {
    return !anySatisfy(selectedFiles, fileIsUnderCvsCondition());
  }

  private static FileCondition fileIsUnderCvsCondition() {
    return new FileCondition() {
      public boolean verify(File file) {
        return fileIsUnderCvs(file);
      }
    };
  }

  private static boolean allSatisfy(File[] files, FileCondition condition) {
    for (File file : files) {
      if (!condition.verify(file)) return false;
    }
    return true;
  }

  private static boolean anySatisfy(File[] files, FileCondition condition) {
    return !allSatisfy(files, new ReverseFileCondition(condition));
  }

  public static boolean filesHaveParentUnderCvs(File[] files) {
    return allSatisfy(files, new FileCondition() {
      public boolean verify(File file) {
        return fileHasParentUnderCvs(file);
      }
    });
  }

  private static boolean fileHasParentUnderCvs(File file) {
    return fileIsUnderCvs(file.getParentFile());
  }

  public static boolean fileIsLocallyAdded(File file) {
    Entry entry = getEntryFor(file);
    return entry != null && entry.isAddedFile();
  }

  public static boolean fileIsLocallyDeleted(File file) {
    Entry entry = getEntryFor(file);
    return entry != null && entry.isRemoved();
  }

  public static boolean fileIsLocallyAdded(VirtualFile file) {
    return fileIsLocallyAdded(CvsVfsUtil.getFileFor(file));
  }

  public static Entries getEntriesIn(File dir) {
    return getEntriesHandlerIn(dir).getEntries();
  }

  private static EntriesHandler getEntriesHandlerIn(final File dir) {
    EntriesHandler entriesHandler = new EntriesHandler(dir);
    try {
      entriesHandler.read(CvsApplicationLevelConfiguration.getCharset());
      return entriesHandler;
    }
    catch (Exception ex) {
      final String entries = loadFrom(dir, ENTRIES, true);
      if (entries != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          final String entriesFileRelativePath = CVS + File.separatorChar + ENTRIES;
          Messages.showErrorDialog(
            CvsBundle.message("message.error.invalid.entries", entriesFileRelativePath, dir.getAbsolutePath(), entries),
            CvsBundle.message("message.error.invalid.entries.title"));
        });
      }
      return entriesHandler;
    }
  }

  public static void removeEntryFor(File file) {
    File entriesFile = file.getParentFile();
    EntriesHandler handler = new EntriesHandler(entriesFile);
    String charset = CvsApplicationLevelConfiguration.getCharset();
    try {
      handler.read(charset);
    }
    catch (IOException e) {
      return;
    }
    Entries entries = handler.getEntries();
    entries.removeEntry(file.getName());

    try {
      handler.write(getLineSeparator(), charset);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    CvsEntriesManager.getInstance().removeEntryForFile(file.getParentFile(), file.getName());
  }

  private static String getLineSeparator() {
    return CodeStyleFacade.getInstance().getLineSeparator();
  }

  public static boolean fileIsLocallyRemoved(File file) {
    Entry entry = getEntryFor(file);
    if (entry == null) return false;
    return entry.isRemoved();
  }

  public static boolean fileIsLocallyRemoved(VirtualFile file) {
    return fileIsLocallyRemoved(CvsVfsUtil.getFileFor(file));
  }

  public static String formatDate(Date date) {
    return DATE_FORMATTER.format(date);
  }

  public static void saveEntryForFile(File file, Entry entry) throws IOException {
    EntriesHandler entriesHandler = new EntriesHandler(file.getParentFile());
    entriesHandler.read(CvsApplicationLevelConfiguration.getCharset());
    entriesHandler.getEntries().addEntry(entry);
    entriesHandler.write(getLineSeparator(), CvsApplicationLevelConfiguration.getCharset());
  }

  public static String loadRepositoryFrom(File file) {
    return loadFrom(file, REPOSITORY, true);
  }

  public static String loadRootFrom(File file) {
    return loadFrom(file, CVS_ROOT_FILE, true);
  }

  @Nullable
  private static String loadFrom(File directory, String fileName, boolean trimContent) {
    if (directory == null) return null;
    File file = getFileInTheAdminDir(directory, fileName);
    if (!file.isFile()) return null;
    try {
      String result = FileUtil.loadFile(file);
      if (trimContent) {
        return result.trim();
      }
      else {
        return result;
      }
    }
    catch (IOException e) {
      return null;
    }
  }

  private static File getFileInTheAdminDir(File file, String fileName) {
    return new File(getAdminDir(file), fileName);
  }

  private static File getAdminDir(File file) {
    return new File(file, CVS);
  }

  private static VirtualFile getAdminDir(VirtualFile file) {
    VirtualFile child = file.findChild(CVS);
    return child != null && child.isDirectory() ? child:null;
  }

  @Nullable
  public static String getStickyDateForDirectory(VirtualFile parentFile) {
    File file = CvsVfsUtil.getFileFor(parentFile);
    return getStickyDateForDirectory(file);
  }

  @Nullable
  public static String getStickyDateForDirectory(final File file) {
    String tag = loadStickyTagFrom(file);
    if (tag == null) return null;
    if (tag.startsWith(STICKY_DATE_PREFIX)) {
      return tag.substring(STICKY_DATE_PREFIX.length());
    }
    if (tag.startsWith(STICKY_BRANCH_TAG_PREFIX)) {
      return tag.substring(STICKY_BRANCH_TAG_PREFIX.length());
    }

    return tag;
  }

  public static String loadStickyTagFrom(File file) {
    return loadFrom(file, TAG, true);
  }

  @Nullable
  public static String getStickyTagForDirectory(VirtualFile parentFile) {
    return getStickyTagForDirectory(CvsVfsUtil.getFileFor(parentFile));
  }

  @Nullable
  public static String getStickyTagForDirectory(File ioFile) {
    String tag = loadFrom(ioFile, TAG, true);
    if (tag == null) return null;
    if (tag.length() == 0) return null;
    if (tag.startsWith(STICKY_DATE_PREFIX)) return null;
    if (tag.startsWith(STICKY_BRANCH_TAG_PREFIX)) return tag.substring(1);
    if (tag.startsWith(STICKY_NON_BRANCH_TAG_PREFIX)) return tag.substring(1);
    return null;
  }

  public static void ignoreFile(@NotNull final VirtualFile file) throws IOException {
    VirtualFile directory = file.getParent();
    File cvsignoreFile = cvsignoreFileFor(directory == null ? "" : directory.getPath());
    CvsFileUtil.appendLineToFile(file.getName(), cvsignoreFile);
    CvsEntriesManager.getInstance().clearCachedFiltersFor(directory);
  }

  public static File cvsignoreFileFor(String path) {
    return new File(new File(path), CVS_IGNORE_FILE);
  }

  public static File cvsignoreFileFor(File file) {
    return new File(file, CVS_IGNORE_FILE);
  }

  public static void addConflict(File file) {
    File conflictsFile = getConflictsFile(file);
    try {
      Conflicts conflicts = Conflicts.readFrom(conflictsFile);
      conflicts.addConflictForFile(file.getName());
      conflicts.saveTo(conflictsFile);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static File getConflictsFile(File file) {
    return getFileInTheAdminDir(file.getParentFile(), CONFLICTS);
  }

  public static void removeConflict(File file) {
    File conflictsFile = getConflictsFile(file);
    if (!conflictsFile.exists()) return;
    try {
      Conflicts conflicts = Conflicts.readFrom(conflictsFile);
      conflicts.removeConflictForFile(file.getName());
      conflicts.saveTo(conflictsFile);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static boolean isLocallyRemoved(File file) {
    Entry entry = getEntryFor(file);
    if (entry == null) return false;
    return entry.isRemoved();
  }

  public static String getRevisionFor(File file) {
    final Entry entry = getEntryFor(file);
    if (entry == null) return null;
    return entry.getRevision();
  }

  public static boolean filesExistInCvs(File[] files) {
    return allSatisfy(files, new FileCondition() {
      public boolean verify(File file) {
        return fileIsUnderCvs(file) && !fileIsLocallyAdded(file);
      }
    });
  }

  public static boolean filesAreNotDeleted(File[] files) {
    return allSatisfy(files, new FileCondition() {
      public boolean verify(File file) {
        return fileIsUnderCvs(file)
               && !fileIsLocallyAdded(file)
               && !fileIsLocallyDeleted(file);
      }
    });
  }

  public static void saveRevisionForMergedFile(@NotNull VirtualFile parent,
                                               @NotNull final Entry previousEntry,
                                               List<String> revisions) {
    File conflictsFile = getConflictsFile(new File(CvsVfsUtil.getFileFor(parent), previousEntry.getFileName()));
    try {
      Conflicts conflicts = Conflicts.readFrom(conflictsFile);
      Date lastModified = previousEntry.getLastModified();
      conflicts.setRevisionAndDateForFile(previousEntry.getFileName(),
                                          previousEntry.getRevision(),
                                          revisions,
                                          lastModified == null ? new Date().getTime() : lastModified.getTime());
      conflicts.saveTo(conflictsFile);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static byte[] getStoredContentForFile(VirtualFile file, final String originalRevision) {
    File ioFile = CvsVfsUtil.getFileFor(file);
    try {
      File storedRevisionFile = new File(ioFile.getParentFile(), ".#" + ioFile.getName() + "." + originalRevision);
      if (!storedRevisionFile.isFile()) return null;
      return FileUtil.loadFileBytes(storedRevisionFile);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static boolean haveCachedContent(final VirtualFile file, final String revision) {
    final File storedRevisionFile = createFromRevisionAndPath(file, revision);
    return (storedRevisionFile != null) && storedRevisionFile.isFile();
  }

  @Nullable
  private static File createFromRevisionAndPath(final VirtualFile file, final String revision) {
    File ioFile = CvsVfsUtil.getFileFor(file);
    final File parent = new File(getAdminDir(ioFile.getParentFile()), BASE_REVISIONS_DIR);
    if (! parent.exists()) {
      if (! parent.mkdirs()) {
        return null;
      }
    } else if (parent.isFile()) {
      return null;
    }
    return new File(parent, ".#" + ioFile.getName() + "." + revision);
  }

  private static File getCachedContentFile(final VirtualFile parent, String name, String revision) {
    final File parentFile = new File(getAdminDir(new File(parent.getPath())), BASE_REVISIONS_DIR);
    final File storedRevisionFile;
    if (revision.startsWith("-")) {
      storedRevisionFile = new File(parentFile, ".#" + name + '.' + revision.substring(1));
    } else {
      storedRevisionFile = new File(parentFile, ".#" + name + '.' + revision);
    }
    if ((! storedRevisionFile.exists()) || (! storedRevisionFile.isFile())) return null;
    return storedRevisionFile;
  }

  @Nullable
  public static byte[] getCachedStoredContent(final VirtualFile parent, final String name, final String revision) {
    try {
      File storedRevisionFile = getCachedContentFile(parent, name, revision);
      if (storedRevisionFile == null) return null;
      return FileUtil.loadFileBytes(storedRevisionFile);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static boolean restoreFileFromCachedContent(final VirtualFile parent,
                                                     final String name,
                                                     final String revision,
                                                     boolean makeReadOnly) {
    try {
      final File cachedContentFile = getCachedContentFile(parent, name, revision);
      if (cachedContentFile == null) return false;
      final byte[] content = FileUtil.loadFileBytes(cachedContentFile);
      final File file = new File(parent.getPath(), name);
      FileUtil.createIfDoesntExist(file);
      if (!file.canWrite() && !file.setWritable(true)) return false;
      FileUtil.writeToFile(file, content);
      if (makeReadOnly && !file.setWritable(false)) return false;
      return file.setLastModified(cachedContentFile.lastModified());
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
  }

  public static void storeContentForRevision(final VirtualFile file, final String revision, final byte[] bytes) {
    final File storedRevisionFile = createFromRevisionAndPath(file, revision);
    if (storedRevisionFile == null) {
      return;
    }
    // already exists
    if (storedRevisionFile.isFile()) return;
    try {
      FileUtil.writeToFile(storedRevisionFile, bytes);
      storedRevisionFile.setLastModified(file.getTimeStamp());
    }
    catch (IOException e) {
      LOG.info(e);
    }
    deleteAllOtherRevisions(file, storedRevisionFile.getName());
  }

  private static void deleteAllOtherRevisions(final VirtualFile file, final String storedFilename) {
    File ioFile = new File(file.getPath());
    final Pattern pattern = Pattern.compile("\\Q.#" + ioFile.getName() + ".\\E" + REVISION_PATTERN);
    final File dir = new File(getAdminDir(ioFile.getParentFile()), BASE_REVISIONS_DIR);
    File[] files = dir.listFiles((dir1, name) -> (!storedFilename.equals(name)) && pattern.matcher(name).matches());
    if (files != null) {
      for (File oldFile : files) {
        oldFile.delete();
      }
    }
  }

  public static byte[] getStoredContentForFile(VirtualFile file) {
    File ioFile = CvsVfsUtil.getFileFor(file);
    try {
      File storedRevisionFile = new File(ioFile.getParentFile(), ".#" + ioFile.getName() + "." + getAllRevisionsForFile(file).get(0));
      if (!storedRevisionFile.isFile()) return null;
      return FileUtil.loadFileBytes(storedRevisionFile);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static long getUpToDateDateForFile(VirtualFile file) {
    try {
      return Conflicts.readFrom(getConflictsFile(CvsVfsUtil.getFileFor(file))).getPreviousEntryTime(file.getName());
    }
    catch (IOException e) {
      LOG.error(e);
      return -1;
    }
  }

  public static String getOriginalRevisionForFile(VirtualFile file) {
    try {
      return Conflicts.readFrom(getConflictsFile(CvsVfsUtil.getFileFor(file))).getOriginalRevisionFor(file.getName());
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static void resolveConflict(VirtualFile vFile) {
    File file = CvsVfsUtil.getFileFor(vFile);
    removeConflict(file);

    EntriesHandler handler = getEntriesHandlerIn(file.getParentFile());
    Entries entries = handler.getEntries();
    Entry entry = entries.getEntry(file.getName());
    if (entry == null) return;
    long timeStamp = vFile.getTimeStamp();
    final Date date = CvsStatusProvider.createDateDiffersTo(timeStamp);
    entry.parseConflictString(Entry.getLastModifiedDateFormatter().format(date));
    entries.addEntry(entry);
    try {
      handler.write(getLineSeparator(), CvsApplicationLevelConfiguration.getCharset());
    }
    catch (IOException e) {
      LOG.error(e);
    }

  }

  @Nullable
  public static String getTemplateFor(FilePath file) {
    return loadFrom(file.isDirectory() ? file.getIOFile().getParentFile() : file.getIOFile(), TEMPLATE, false);
  }

  public static String getRepositoryFor(File file) {
    String result = loadRepositoryFrom(file);

    if (result == null) return null;

    String root = loadRootFrom(file);

    if (root != null) {
      final CvsRootParser cvsRootParser = CvsRootParser.valueOf(root, false);
      String serverRoot = cvsRootParser.REPOSITORY;
      if (serverRoot != null) {
        result = getRelativeRepositoryPath(result, serverRoot);
      }
    }

    return result;

  }

  public static String getRelativeRepositoryPath(String repository, String serverRoot) {
    repository = repository.replace(File.separatorChar, '/');
    serverRoot = serverRoot.replace(File.separatorChar, '/');

    if (repository.startsWith(serverRoot)) {
      repository = repository.substring(serverRoot.length());

      repository = StringUtil.trimStart(repository, "/");
    }

    repository = StringUtil.trimStart(repository, "./");

    return repository;
  }

  public static File getCvsLightweightFileForFile(File file) {
    return new File(getRepositoryFor(file.getParentFile()), file.getName());
  }

  public static List<String> getAllRevisionsForFile(VirtualFile file) {
    try {
      return Conflicts.readFrom(getConflictsFile(CvsVfsUtil.getFileFor(file))).getRevisionsFor(file.getName());
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }

  }

  public static void restoreFile(final VirtualFile file) {
    CvsEntriesManager cvsEntriesManager = CvsEntriesManager.getInstance();
    VirtualFile directory = file == null ? null : file.getParent();
    LOG.assertTrue(directory != null);
    CvsInfo cvsInfo = cvsEntriesManager.getCvsInfoFor(directory);
    Entry entry = cvsInfo.getEntryNamed(file.getName());
    LOG.assertTrue(entry != null);
    String revision = entry.getRevision();
    LOG.assertTrue(StringUtil.startsWithChar(revision, '-'));
    String originalRevision = revision.substring(1);
    String date = Entry.formatLastModifiedDate(CvsStatusProvider.createDateDiffersTo(file.getTimeStamp()));
    String kwdSubstitution = entry.getOptions() == null ? "" : entry.getOptions();
    String stickyDataString = entry.getStickyData();
    Entry newEntry = Entry.createEntryForLine("/" + file.getName() + "/" + originalRevision + "/" + date + "/" + kwdSubstitution + "/" + stickyDataString);
    try {
      saveEntryForFile(CvsVfsUtil.getFileFor(file), newEntry);
      cvsEntriesManager.clearCachedEntriesFor(directory);
    }

    catch (final IOException e) {
      SwingUtilities.invokeLater(() -> Messages.showErrorDialog(CvsBundle.message("message.error.restore.entry", file.getPresentableUrl(), e.getLocalizedMessage()),
                                                            CvsBundle.message("message.error.restore.entry.title")));
    }
  }

  public static boolean fileExistsInCvs(VirtualFile file) {
    if (file.isDirectory()) {
      final VirtualFile child = file.findChild(CVS);
      if (child != null && child.isDirectory()) return true;
    }

    Entry entry = CvsEntriesManager.getInstance().getEntryFor(file);
    if (entry == null) return false;
    return !entry.isAddedFile();
  }

  public static boolean fileExistsInCvs(FilePath file) {
    if (file.isDirectory() && new File(file.getIOFile(), CVS).isDirectory()) return true;
    Entry entry = CvsEntriesManager.getInstance().getEntryFor(file.getVirtualFileParent(), file.getName());
    if (entry == null) return false;
    return !entry.isAddedFile();
  }


  public static boolean storedVersionExists(final String original, final VirtualFile file) {
    File ioFile = CvsVfsUtil.getFileFor(file);
    File storedRevisionFile = new File(ioFile.getParentFile(), ".#" + ioFile.getName() + "." + original);
    return storedRevisionFile.isFile();
  }

  public static boolean isNonDateTag(final String dirTag) {
    return dirTag.startsWith(STICKY_BRANCH_TAG_PREFIX) || dirTag.startsWith(STICKY_NON_BRANCH_TAG_PREFIX);
  }

  private static interface FileCondition {
    boolean verify(File file);
  }

  private static class ReverseFileCondition implements FileCondition {
    private final FileCondition myCondition;

    public ReverseFileCondition(FileCondition condition) {
      myCondition = condition;
    }

    public boolean verify(File file) {
      return !myCondition.verify(file);
    }
  }

  private static class Conflict {
    private final String myName;
    private final List<String> myRevisions;
    private final long myPreviousTime;
    private static final String DELIM = ";";

    private Conflict(String name, String originalRevision, List<String> revisions, long time) {
      myName = name;
      myRevisions = new ArrayList<>();
      myRevisions.add(originalRevision);
      myRevisions.addAll(revisions);
      myPreviousTime = time;
    }

    private Conflict(String name, List<String> revisions, long time) {
      myName = name;
      myRevisions = new ArrayList<>();
      myRevisions.addAll(revisions);
      myPreviousTime = time;
    }

    public String toString() {
      StringBuffer result = new StringBuffer();
      result.append(myName);
      result.append(DELIM);
      result.append(String.valueOf(myPreviousTime));
      result.append(DELIM);
      for (int i = 0; i < myRevisions.size(); i++) {
        if (i > 0) {
          result.append(DELIM);
        }
        result.append(myRevisions.get(i));
      }
      return result.toString();
    }

    public static Conflict readFrom(String line) {
      try {
        String[] strings = line.split(DELIM);
        if (strings.length == 0) return null;
        String name = strings[0];
        long time = strings.length > 1 ? Long.parseLong(strings[1]) : -1;

        int revisionsSize = strings.length > 2 ? strings.length - 2 : 0;
        String[] revisions = new String[revisionsSize];
        if (revisions.length > 0) {
          System.arraycopy(strings, 2, revisions, 0, revisions.length);
        }

        return new Conflict(name, Arrays.asList(revisions), time);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }

    public String getFileName() {
      return myName;
    }

    public long getPreviousEntryTime() {
      return myPreviousTime;
    }

    public List<String> getRevisions() {
      return new ArrayList<>(myRevisions);
    }

    public void setOriginalRevision(final String originalRevision) {
      if (!myRevisions.isEmpty()) myRevisions.remove(0);
      myRevisions.add(0, originalRevision);
    }

    public void setRevisions(final List<String> revisions) {
      if (myRevisions.isEmpty()) {

      } else {
        final String originalRevision = myRevisions.remove(0);
        myRevisions.clear();
        myRevisions.add(originalRevision);
        myRevisions.addAll(revisions);
      }
    }
  }

  private static class Conflicts {
    private final Map<String, Conflict> myNameToConflict = new com.intellij.util.containers.HashMap<>();

    @NotNull
    public static Conflicts readFrom(File file) throws IOException {
      Conflicts result = new Conflicts();
      if (!file.exists()) return result;
      List<String> lines = CvsFileUtil.readLinesFrom(file);
      for (final String line : lines) {
        Conflict conflict = Conflict.readFrom(line);
        if (conflict != null) {
          result.addConflict(conflict);
        }
      }
      return result;
    }


    public void saveTo(File file) throws IOException {
      CvsFileUtil.storeLines(getConflictLines(), file);
    }

    private List<String> getConflictLines() {
      ArrayList<String> result = new ArrayList<>();
      for (final Conflict conflict : myNameToConflict.values()) {
        result.add((conflict).toString());
      }
      return result;
    }

    private void addConflict(Conflict conflict) {
      myNameToConflict.put(conflict.getFileName(), conflict);
    }

    public void setRevisionAndDateForFile(String fileName,
                                          String originalRevision,
                                          List<String> revisions,
                                          long time) {
      if (!myNameToConflict.containsKey(fileName)) {
        myNameToConflict.put(fileName, new Conflict(fileName, originalRevision, revisions, time));
      }
      myNameToConflict.get(fileName).setOriginalRevision(originalRevision);
      myNameToConflict.get(fileName).setRevisions(revisions);
    }

    public void addConflictForFile(String name) {
      if (!myNameToConflict.containsKey(name)) {
        myNameToConflict.put(name, new Conflict(name, "", new ArrayList<>(), -1));
      }
    }

    public void removeConflictForFile(String name) {
      myNameToConflict.remove(name);
    }

    public List<String> getRevisionsFor(String name) {
      if (!myNameToConflict.containsKey(name)) return new ArrayList<>();
      return (myNameToConflict.get(name)).getRevisions();
    }

    public long getPreviousEntryTime(String fileName) {
      if (!myNameToConflict.containsKey(fileName)) return -1;
      return (myNameToConflict.get(fileName)).getPreviousEntryTime();
    }

    public String getOriginalRevisionFor(final String name) {
      if (!myNameToConflict.containsKey(name)) return "";
      final List<String> revisions = (myNameToConflict.get(name)).getRevisions();
      return revisions.isEmpty() ? "" : revisions.get(0);
    }
  }

  public static CvsConnectionSettings getCvsConnectionSettings(FilePath path){
    VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile == null || !path.isDirectory()){
      return CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(path.getVirtualFileParent());
    } else {
      return CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(virtualFile);
    }
  }

}
