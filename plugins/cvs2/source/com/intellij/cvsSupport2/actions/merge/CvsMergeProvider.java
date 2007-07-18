/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.actions.merge;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 29, 2005
 * Time: 12:48:39 AM
 * To change this template use File | Settings | File Templates.
 */
public class CvsMergeProvider implements MergeProvider {
  @NotNull
  private final Map<VirtualFile, List<String>> myFileToRevisions;

  private final Map<VirtualFile, MergeDataProvider> myFileToMergeData = new HashMap<VirtualFile, MergeDataProvider>();

  private final Project myProject;

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.actions.merge.CvsMergeProvider");

  public CvsMergeProvider(final Map<VirtualFile, List<String>> fileToRevisions, Project project) {
    myFileToRevisions = fileToRevisions;
    myProject = project;
  }

  @NotNull
  public MergeData loadRevisions(final VirtualFile file) throws VcsException {
    return ensureMergeData(file).createData();
  }

  public void conflictResolvedForFile(VirtualFile file) {
    CvsUtil.resolveConflict(file);
  }

  public boolean isBinary(final VirtualFile file) {
    return false;
  }

  private MergeDataProvider ensureMergeData(final VirtualFile file) {
    if (myFileToMergeData.containsKey(file)) return myFileToMergeData.get(file);


    final List<String> revisions = getRevisions(file);

    if (revisions.isEmpty()) {
      return saveMergeData(file, parseConflictsInFile(file));
    }

    final String lastRevision = CvsUtil.getEntryFor(file).getRevision();

    if (revisions.size() == 1) {
      final String original = revisions.get(0);
      return saveMergeData(file, new MergeInfo(true, original, original, lastRevision, file, myProject));
    }
    Collection<String> candidates = new LinkedHashSet<String>();
    final String original = revisions.get(0);
    if (CvsUtil.storedVersionExists(original, file)) {
      candidates.add(original + "#");
    }
    candidates.add(original);
    candidates.addAll(revisions);
    candidates.add(lastRevision);

    LOG.assertTrue(candidates.size() >= 2);

    final ArrayList<String> candidatesList = new ArrayList<String>(candidates);

    if (candidatesList.size() == 2) {
      return saveMergeData(file, new MergeInfo(true, original, candidatesList.get(1), lastRevision, file, myProject));
    }

    final String originalCandidate = candidatesList.get(0);
    String resultRevision = originalCandidate;
    boolean useStored = false;
    if (originalCandidate.endsWith("#")) {
      useStored = true;
      resultRevision = resultRevision.substring(0, resultRevision.length() - 1);
    }

    if (candidates.size() == 3) {
      return saveMergeData(file ,new MergeInfo(useStored, resultRevision, candidatesList.get(2), candidatesList.get(1),
                        file, myProject));
    }
    else {
      if (useStored) {
        candidatesList.remove(0);
      }
      return saveMergeData(file ,parseConflictsInFile(file));
    }

  }

  private MergeDataProvider saveMergeData(final VirtualFile file, final MergeDataProvider result) {
    myFileToMergeData.put(file, result);
    return result;
  }

  @NotNull private static MergeDataProvider parseConflictsInFile(final VirtualFile file) {
    return new MergeDataProvider() {
      @NotNull
      public MergeData createData() throws VcsException {
        try {
          BufferedInputStream input = null;
          try {
            input = new BufferedInputStream(new FileInputStream(CvsVfsUtil.getFileFor(file)));
            final CvsConflictsParser parser = CvsConflictsParser.createOn(input);
            final MergeData mergeData = new MergeData();
            mergeData.ORIGINAL = parser.getCenterVersion().getBytes();
            mergeData.CURRENT = parser.getLeftVersion().getBytes();
            mergeData.LAST = parser.getRightVersion().getBytes();
            return mergeData;
          }
          finally {
            if (input != null) {
              input.close();
            }
          }
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
      }
    };
  }

  private List<String> getRevisions(final VirtualFile file) {
    return myFileToRevisions.get(file);
  }

}
