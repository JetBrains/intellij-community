/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.util.*;

public class CvsChangeListsBuilder {
  @NonNls private static final String INITIALLY_ADDED_ON_BRANCH = "was initially added on branch";
  @NonNls private static final String ATTIC_SUFFIX = "/Attic";

  private static class ChangeListKey {
    public String branch;
    public String author;
    public String message;

    public ChangeListKey(final String branch, final String author, final String message) {
      this.branch = branch;
      this.author = author;
      this.message = message;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ChangeListKey that = (ChangeListKey)o;

      if (!author.equals(that.author)) return false;
      if (branch != null ? !branch.equals(that.branch) : that.branch != null) return false;
      if (!message.equals(that.message)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (branch != null ? branch.hashCode() : 0);
      result = 31 * result + author.hashCode();
      result = 31 * result + message.hashCode();
      return result;
    }
  }

  private final Map<ChangeListKey, List<CvsChangeList>> myCache = new HashMap<ChangeListKey, List<CvsChangeList>>();

  private long myLastNumber = 0;
  private final String myRootPath;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;
  private final VirtualFile myRootFile;

  public CvsChangeListsBuilder(final String rootPath, final CvsEnvironment environment, final Project project,
                               final VirtualFile rootFile) {
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
    myRootFile = rootFile;
  }

  public List<CvsChangeList> getVersions() {
    final ArrayList<CvsChangeList> result = new ArrayList<CvsChangeList>();
    for (List<CvsChangeList> versions : myCache.values()) {
      result.addAll(versions);
    }
    return result;
  }

  public CvsChangeList addRevision(RevisionWrapper revision) {
    final Revision cvsRevision = revision.getRevision();
    CvsChangeList version = findOrCreateVersionFor(cvsRevision.getMessage(),
                                                   revision.getTime(),
                                                   cvsRevision.getAuthor(),
                                                   revision.getBranch(),
                                                   revision.getFile());

    version.addFileRevision(revision);
    return version;
  }

  private CvsChangeList findOrCreateVersionFor(final String message, final long date, final String author,
                                               final String branch, final String path) {
    final ChangeListKey key = new ChangeListKey(branch, author, message);
    final List<CvsChangeList> versions = myCache.get(key);
    if (versions != null) {
      final CvsChangeList lastVersion = versions.get(versions.size() - 1);
      if (lastVersion.containsDate(date) && !lastVersion.containsFile(path)) {
        return lastVersion;
      }
    }

    final CvsChangeList result = new CvsChangeList(myProject, myEnvironment, myRootFile,
                                                   myLastNumber, message, date, author, myRootPath);
    myLastNumber += 1;

    if (!myCache.containsKey(key)) {
      myCache.put(key, new ArrayList<CvsChangeList>());
    }

    myCache.get(key).add(result);
    return result;
  }

  @Nullable
  public List<RevisionWrapper> revisionWrappersFromLog(final LogInformationWrapper log) {
    final List<RevisionWrapper> result = new LinkedList<RevisionWrapper>();
    final String file = log.getFile();
    if (CvsChangeList.isAncestor(myRootPath, file)) {
      for (Revision revision : log.getRevisions()) {
        if (revision != null) {
          if (revision.getState().equals(CvsChangeList.DEAD_STATE) &&
              revision.getMessage().indexOf(INITIALLY_ADDED_ON_BRANCH) >= 0) {
            // ignore dead revision (otherwise it'll get stuck in incoming changes forever - it's considered a deletion and
            // the file is never actually deleted)
            continue;
          }
          String branchName = getBranchName(revision, log.getSymbolicNames());
          result.add(new RevisionWrapper(stripAttic(file), revision, branchName));
        }
      }
      return result;
    }
    return null;
  }

  public void addLogs(final List<LogInformationWrapper> logs) {
    List<RevisionWrapper> revisionWrappers = new ArrayList<RevisionWrapper>();

    for (LogInformationWrapper log : logs) {
      final List<RevisionWrapper> wrappers = revisionWrappersFromLog(log);
      if (wrappers != null) {
        revisionWrappers.addAll(wrappers);
      }
    }

    Collections.sort(revisionWrappers);


    for (RevisionWrapper revisionWrapper : revisionWrappers) {
      addRevision(revisionWrapper);
    }
  }

  private static String stripAttic(final String file) {
    int pos = file.lastIndexOf('/');
    if (pos >= 0) {
      String path = file.substring(0, pos);
      if (path.endsWith(ATTIC_SUFFIX)) {
        return path.substring(0, path.length()-6) + file.substring(pos);
      }
    }
    return file;
  }

  @Nullable
  private static String getBranchName(final Revision revision, final List<SymbolicName> symbolicNames) {
    CvsRevisionNumber number = new CvsRevisionNumber(revision.getNumber().trim());
    final int[] subRevisions = number.getSubRevisions();
    String branchName = null;
    String branchNumberString = null;
    if (subRevisions != null && subRevisions.length >= 4) {
      int branchRevNumber = subRevisions [subRevisions.length-2];
      CvsRevisionNumber branchNumber = number.removeTailVersions(2).addTailVersions(0, branchRevNumber);
      branchNumberString = branchNumber.asString();
    }
    if (branchNumberString == null) {
      final String branches = revision.getBranches();
      if (branches != null && branches.length() > 0) {
        final String[] branchNames = branches.split(";");
        final CvsRevisionNumber revisionNumber = new CvsRevisionNumber(branchNames [0].trim());
        int[] branchSubRevisions = revisionNumber.getSubRevisions();
        assert branchSubRevisions != null;
        int rev = branchSubRevisions [branchSubRevisions.length-1];
        CvsRevisionNumber branchNumber = revisionNumber.removeTailVersions(1).addTailVersions(0, rev);
        branchNumberString = branchNumber.asString();
      }
    }
    if (branchNumberString != null) {
      for(SymbolicName name: symbolicNames) {
        if (name.getRevision().equals(branchNumberString)) {
          branchName = name.getName();
          break;
        }
      }
    }

    return branchName;
  }
}
