package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class UpdatedFilesManager implements IMessageListener {
  private final static Pattern MERGE_PATTERN = Pattern.compile("(cvs server: revision )(.*)( from repository is now in )(.*)");
  private final static Pattern MERGING_DIFFERENCES_PATTERN = Pattern.compile("(Merging differences between )(.*)( and )(.*)( into )(.*)");

  private final static String MERGED_FILE_MESSAGE_PREFIX = "RCS file: ";
  private final static String MERGED_FILE_MESSAGE_POSTFIX = ",v";

  private final Map<File, Collection<String>> myMergedFiles = new HashMap<File, Collection<String>>();
  private final Set<File> myCreatedBySecondParty = new HashSet<File>();

  private ICvsFileSystem myCvsFileSystem;
  public static final String CREATED_BY_SECOND_PARTY_PREFIX = "cvs server: conflict: ";
  public static final String CREATED_BY_SECOND_PARTY_POSTFIX = " created independently by second party";
  private File myCurrentMergedFile;
  private final Collection<Entry> myNewlyCreatedEntries = new HashSet<Entry>();
  private Collection<File> myNonUpdatedFiles = new HashSet<File>();


  public UpdatedFilesManager() {
  }

  public void setCvsFileSystem(ICvsFileSystem cvsFileSystem) {
    myCvsFileSystem = cvsFileSystem;
  }

  public void messageSent(String message, boolean error, boolean tagged) {
    if (message.startsWith(MERGED_FILE_MESSAGE_PREFIX)) {
      String pathInRepository = message.substring(MERGED_FILE_MESSAGE_PREFIX.length(),
                                                  message.length()
                                                  -
                                                  MERGED_FILE_MESSAGE_POSTFIX.length());
      String relativeRepositoryPath = myCvsFileSystem.getRelativeRepositoryPath(pathInRepository);
      myCurrentMergedFile = myCvsFileSystem.getLocalFileSystem().getFile(removeModuleNameFrom(relativeRepositoryPath));
      ensureFileIsInMap(myCurrentMergedFile);
    }
    else if (message.startsWith(CREATED_BY_SECOND_PARTY_PREFIX) && message.endsWith(CREATED_BY_SECOND_PARTY_POSTFIX)) {
      String pathInRepository = message.substring(CREATED_BY_SECOND_PARTY_PREFIX.length(),
                                                  message.length()
                                                  -
                                                  CREATED_BY_SECOND_PARTY_POSTFIX.length());
      File ioFile = myCvsFileSystem.getLocalFileSystem().getFile(pathInRepository);
      String relativeRepositoryPath = myCvsFileSystem.getRelativeRepositoryPath(ioFile.getPath());
      File file = myCvsFileSystem.getLocalFileSystem().getFile(removeModuleNameFrom(relativeRepositoryPath));
      myCreatedBySecondParty.add(file);

    }
    else if (MERGE_PATTERN.matcher(message).matches()) {
      Matcher matcher = MERGE_PATTERN.matcher(message);
      if (matcher.matches()) {
        String relativeFileName = matcher.group(4);
        File file = myCvsFileSystem.getLocalFileSystem().getFile(relativeFileName);
        ensureFileIsInMap(file);
      }
    }
    else if (MERGING_DIFFERENCES_PATTERN.matcher(message).matches()) {
      Matcher matcher = MERGING_DIFFERENCES_PATTERN.matcher(message);
      if (matcher.matches()) {
        String firstRevision = matcher.group(2);
        String secondRevision = matcher.group(4);
        ensureFileIsInMap(myCurrentMergedFile);
        Collection<String> revisions = myMergedFiles.get(myCurrentMergedFile);
        revisions.add(firstRevision);
        revisions.add(secondRevision);
      }
    }
  }

  private void ensureFileIsInMap(File file) {
    if (!myMergedFiles.containsKey(file)) {
      myMergedFiles.put(file, new ArrayList<String>());
    }
  }

  private String removeModuleNameFrom(String relativeRepositoryPath) {
    String moduleName = getModuleNameFor(relativeRepositoryPath);
    if (moduleName == null) return relativeRepositoryPath;
    return relativeRepositoryPath.substring(moduleName.length() + 1);
  }

  private String getModuleNameFor(String relativeRepositoryPath) {
    File file = new File(relativeRepositoryPath);
    if (file.getParentFile() == null) return null;
    while (file.getParentFile() != null) file = file.getParentFile();
    return file.getName();
  }

  public boolean isMerged(File file) {
    return myMergedFiles.containsKey(file);
  }


  public boolean isCreatedBySecondParty(File file) {
    return myCreatedBySecondParty.contains(file);
  }

  public Collection<String> getRevisionsForFile(File file) {
    if (myMergedFiles.containsKey(file)) {
      return myMergedFiles.get(file);
    }
    else {
      return new ArrayList<String>();
    }
  }

  public boolean isNewlyCreatedEntryFor(VirtualFile parent, String name) {
    Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);
    if (entry == null) return true;
    if (entry.getConflictStringWithoutConflict().equals(Entry.DUMMY_TIMESTAMP)) return true;
    return myNewlyCreatedEntries.contains(entry);
  }

  public void addNewlyCreatedEntry(Entry entry) {
    myNewlyCreatedEntries.add(entry);
  }

  public void couldNotUpdateFile(File file) {
    myMergedFiles.remove(file);
    myNonUpdatedFiles.add(file);
  }

  public boolean fileIsNotUpdated(File file) {
    return myNonUpdatedFiles.contains(file);
  }

}
