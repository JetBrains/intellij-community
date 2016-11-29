/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.util.*;

public class CvsChangeListsBuilder {

  @NonNls private static final String INITIALLY_ADDED_ON_BRANCH = "was initially added on branch";

  private static class ChangeListKey extends Trinity<String, String, String> {
    public ChangeListKey(final String branch, final String author, final String message) {
      super(branch, author, message);
    }
  }

  private final Map<ChangeListKey, List<CvsChangeList>> myCache = new HashMap<>();

  private long myLastNumber = 0;
  private final String myRootPath;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;
  private final VirtualFile myRootFile;

  public CvsChangeListsBuilder(final String rootPath, final CvsEnvironment environment, final Project project, final VirtualFile rootFile) {
    myRootPath = rootPath;
    myEnvironment = environment;
    myProject = project;
    myRootFile = rootFile;
  }

  public List<CvsChangeList> getVersions() {
    final ArrayList<CvsChangeList> result = new ArrayList<>();
    for (List<CvsChangeList> versions : myCache.values()) {
      result.addAll(versions);
    }
    return result;
  }

  public CvsChangeList addRevision(RevisionWrapper revisionWrapper) {
    final Revision revision = revisionWrapper.getRevision();
    final CvsChangeList version = findOrCreateVersionFor(revision.getMessage(),
                                                         revisionWrapper.getTime(),
                                                         revision.getAuthor(),
                                                         revisionWrapper.getBranch(),
                                                         revisionWrapper.getFile());
    version.addFileRevision(revisionWrapper);
    return version;
  }

  private CvsChangeList findOrCreateVersionFor(final String message, final long date, final String author,
                                               final String branch, final String path) {
    final ChangeListKey key = new ChangeListKey(branch, author, message);
    final List<CvsChangeList> versions = myCache.get(key);
    if (versions != null) {
      for (int i = versions.size() - 1; i >= 0; i--) {
        final CvsChangeList version = versions.get(i);
        if (version.containsDate(date) && !version.containsFile(path)) {
          return version;
        }
      }
    }
    final CvsChangeList result =
      new CvsChangeList(myProject, myEnvironment, myRootFile, myLastNumber, message, date, author, myRootPath);
    myLastNumber += 1;
    if (!myCache.containsKey(key)) {
      myCache.put(key, new ArrayList<>());
    }
    myCache.get(key).add(result);
    return result;
  }

  @Nullable
  public List<RevisionWrapper> revisionWrappersFromLog(final LogInformationWrapper log) {
    final String file = log.getFile();
    if (!CvsChangeList.isAncestor(myRootPath, file)) {
      return null;
    }
    final List<RevisionWrapper> result = new ArrayList<>();
    for (Revision revision : log.getRevisions()) {
      if (revision != null) {
        if (CvsChangeList.DEAD_STATE.equals(revision.getState()) &&
            revision.getMessage().contains(INITIALLY_ADDED_ON_BRANCH)) {
          // ignore dead revision (otherwise it'll get stuck in incoming changes forever - it's considered a deletion and
          // the file is never actually deleted)
          continue;
        }
        final String branchName = getBranchName(revision, log.getSymbolicNames());
        result.add(new RevisionWrapper(file, revision, branchName));
      }
    }
    return result;
  }

  public void add(LogInformationWrapper log) {
    final List<RevisionWrapper> wrappers = revisionWrappersFromLog(log);
    if (wrappers == null) {
      return;
    }
    for (RevisionWrapper wrapper : wrappers) {
      addRevision(wrapper);
    }
  }

  @Nullable
  private static String getBranchName(final Revision revision, final List<SymbolicName> symbolicNames) {
    final CvsRevisionNumber number = new CvsRevisionNumber(revision.getNumber().trim());
    final int[] subRevisions = number.getSubRevisions();
    String branchNumberString = null;
    if (subRevisions != null && subRevisions.length >= 4) {
      final int branchRevNumber = subRevisions [subRevisions.length-2];
      final CvsRevisionNumber branchNumber = number.removeTailVersions(2).addTailVersions(0, branchRevNumber);
      branchNumberString = branchNumber.asString();
    }
    if (branchNumberString == null) {
      final String branches = revision.getBranches();
      if (branches != null && branches.length() > 0) {
        final String[] branchNames = branches.split(";");
        final CvsRevisionNumber revisionNumber = new CvsRevisionNumber(branchNames [0].trim());
        final int[] branchSubRevisions = revisionNumber.getSubRevisions();
        assert branchSubRevisions != null;
        final int rev = branchSubRevisions [branchSubRevisions.length-1];
        final CvsRevisionNumber branchNumber = revisionNumber.removeTailVersions(1).addTailVersions(0, rev);
        branchNumberString = branchNumber.asString();
      }
    }
    if (branchNumberString != null) {
      for(SymbolicName name: symbolicNames) {
        if (name.getRevision().equals(branchNumberString)) {
          return name.getName();
        }
      }
    }
    return null;
  }
}
