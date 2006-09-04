package com.intellij.cvsSupport2;

import com.intellij.CvsBundle;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.admin.Entries;
import org.netbeans.lib.cvsclient.admin.EntriesHandler;
import org.netbeans.lib.cvsclient.admin.Entry;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * author: lesya
 */
public class CvsUtil {

  private final static SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(Entry.getLastModifiedDateFormatter().toPattern(), Locale.US);

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
  @NonNls private static final String STICKY_DATE_PREFIX = "D";
  @NonNls private static final String TEMPLATE = "Template";
  @NonNls private static final String STICKY_BRANCH_TAG_PREFIX = "T";
  @NonNls private static final String STICKY_NON_BRANCH_TAG_PREFIX = "N";
  @NonNls public static final String HEAD = "HEAD";
  @NonNls public static final String BASE = "Base";

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

  public static Entry getEntryFor(VirtualFile file) {
    return CvsEntriesManager.getInstance().getEntryFor(CvsVfsUtil.getParentFor(file), file.getName());
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
    return entry == null ? false : entry.isAddedFile();
  }

  public static boolean fileIsLocallyDeleted(File file) {
    Entry entry = getEntryFor(file);
    return entry == null ? false : entry.isRemoved();
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
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final String entriesFileRelativePath = CVS + File.separatorChar + ENTRIES;
            Messages.showErrorDialog(CvsBundle.message("message.error.invalid.entries", entriesFileRelativePath, dir.getAbsolutePath(), entries),
                                     CvsBundle.message("message.error.invalid.entries.title"));
          }
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
    return CodeStyleSettingsManager.getInstance().getCurrentSettings().getLineSeparator();
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

  private static String loadFrom(File directory, String fileName, boolean trimContent) {
    if (directory == null) return null;
    File file = getFileInTheAdminDir(directory, fileName);
    if (!file.isFile()) return null;
    try {
      String result = new String(FileUtil.loadFileText(file));
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

  public static String getStickyDateForDirectory(VirtualFile parentFile) {
    File file = CvsVfsUtil.getFileFor(parentFile);
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

  public static String getStickyTagForDirectory(VirtualFile parentFile) {
    String tag = loadFrom(CvsVfsUtil.getFileFor(parentFile), TAG, true);
    if (tag == null) return null;
    if (tag.length() == 0) return null;
    if (tag.startsWith(STICKY_DATE_PREFIX)) return null;
    if (tag.startsWith(STICKY_BRANCH_TAG_PREFIX)) return tag.substring(1);
    if (tag.startsWith(STICKY_NON_BRANCH_TAG_PREFIX)) return tag.substring(1);
    return null;
  }

  public static void ignoreFile(final VirtualFile file) throws IOException {
    VirtualFile directory = CvsVfsUtil.getParentFor(file);
    File cvsignoreFile = cvsignoreFileFor(CvsVfsUtil.getPathFor(directory));
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
        return fileIsUnderCvs(file)
               && !fileIsLocallyAdded(file);
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

  public static void saveRevisionForMergedFile(VirtualFile parent,
                                               final Entry previousEntry,
                                               List<String> revisions) {
    LOG.assertTrue(parent != null);
    LOG.assertTrue(previousEntry != null);
    File conflictsFile = getConflictsFile(new File(CvsVfsUtil.getFileFor(parent), previousEntry.getFileName()));
    try {
      Conflicts conflicts = Conflicts.readFrom(conflictsFile);
      LOG.assertTrue(conflicts != null);
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

  public static byte[]  getStoredContentForFile(VirtualFile file) {
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

      if (repository.startsWith("/")) {
        repository = repository.substring(1);
      }

    }

    if (repository.startsWith("./")) {
      repository = repository.substring(2);
    }

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
    VirtualFile directory = CvsVfsUtil.getParentFor(file);
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
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(CvsBundle.message("message.error.restore.entry", file.getPresentableUrl(), e.getLocalizedMessage()),
                                   CvsBundle.message("message.error.restore.entry.title"));
        }
      });
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


  public static String getOriginalRevisionForFile(VirtualFile file) {
    try {
      return Conflicts.readFrom(getConflictsFile(CvsVfsUtil.getFileFor(file))).getOriginalRevisionFor(file.getName());
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static boolean storedVersionExists(final String original, final VirtualFile file) {
    File ioFile = CvsVfsUtil.getFileFor(file);
    File storedRevisionFile = new File(ioFile.getParentFile(), ".#" + ioFile.getName() + "." + original);
    return storedRevisionFile.isFile();
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
    private String myName;
    private List<String> myRevisions;
    private long myPreviousTime;
    private static final String DELIM = ";";

    private Conflict(String name, String originalRevision, List<String> revisions, long time) {
      myName = name;
      myRevisions = new ArrayList<String>();
      myRevisions.add(originalRevision);
      myRevisions.addAll(revisions);
      myPreviousTime = time;
    }

    private Conflict(String name, List<String> revisions, long time) {
      myName = name;
      myRevisions = new ArrayList<String>();
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
        System.arraycopy(strings, 2, revisions, 0, revisions.length);

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
      return new ArrayList<String>(myRevisions);
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
    private final Map<String, Conflict> myNameToConflict = new com.intellij.util.containers.HashMap<String, Conflict>();

    public static Conflicts readFrom(File file) throws IOException {
      Conflicts result = new Conflicts();
      if (!file.exists()) return result;
      List lines = CvsFileUtil.readLinesFrom(file);
      for (final Object line1 : lines) {
        String line = (String)line1;
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

    private List getConflictLines() {
      ArrayList<String> result = new ArrayList<String>();
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
        myNameToConflict.put(name, new Conflict(name, "", new ArrayList<String>(), -1));
      }
    }

    public void removeConflictForFile(String name) {
      myNameToConflict.remove(name);
    }

    public List<String> getRevisionsFor(String name) {
      if (!myNameToConflict.containsKey(name)) return new ArrayList<String>();
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
