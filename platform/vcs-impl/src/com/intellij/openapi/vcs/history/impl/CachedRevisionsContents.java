/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;

import java.io.IOException;
import java.util.*;

/**
 * @author irengrig
 *
 * Would not be used in concurrent context
 * Prepared in bkgrnd, if needed, asked/updated in AWT
 */
public class CachedRevisionsContents {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.impl.CachedRevisionsContents");
  private final Map<VcsRevisionNumber, String> myCachedContents = new HashMap<VcsRevisionNumber, String>();
  private final Project myProject;
  // managed outside, for reference here
  private List<VcsFileRevision> myRevisions;
  private final VirtualFile myFile;

  public CachedRevisionsContents(final Project project, final VirtualFile file) {
    myProject = project;
    myFile = file;
  }

  public void setRevisions(List<VcsFileRevision> revisions) {
    myRevisions = revisions;
  }

  public void loadContentsFor(final VcsFileRevision[] revisions) {
    final VcsFileRevision[] revisionsToLoad = revisionsNeededToBeLoaded(revisions);

    final List<VcsFileRevision> toBeLoaded = new LinkedList<VcsFileRevision>();
    for (VcsFileRevision revision : revisionsToLoad) {
      if (myCachedContents.containsKey(revision.getRevisionNumber())) continue;
      toBeLoaded.add(revision);
    }
    if (toBeLoaded.isEmpty()) return;

    final Runnable process = new Runnable() {
      public void run() {
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        progressIndicator.pushState();
        // user should see a progress at least by revision numbers...
        Collections.sort(toBeLoaded, new Comparator<VcsFileRevision>() {
          @Override
          public int compare(VcsFileRevision o1, VcsFileRevision o2) {
            return o1.getRevisionNumber().compareTo(o2.getRevisionNumber());
          }
        });
        progressIndicator.setIndeterminate(false);
        try {
          for (int i = 0; i < toBeLoaded.size(); i++) {
            progressIndicator.checkCanceled();
            
            final VcsFileRevision vcsFileRevision = toBeLoaded.get(i);
            progressIndicator.setText2(VcsBundle.message("progress.text2.loading.revision", vcsFileRevision.getRevisionNumber()));
            progressIndicator.setFraction((double)i / (double) toBeLoaded.size());
            if (!myCachedContents.containsKey(vcsFileRevision.getRevisionNumber())) {
              try {
                vcsFileRevision.loadContent();
              }
              catch (final VcsException e) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    Messages.showErrorDialog(VcsBundle.message("message.text.cannot.load.version.because.of.error",
                                                               vcsFileRevision.getRevisionNumber(), e.getLocalizedMessage()),
                                             VcsBundle.message("message.title.load.version"));
                  }
                });
              }
              catch (ProcessCanceledException ex) {
                return;
              }
              String content = null;
              try {
                final byte[] byteContent = vcsFileRevision.getContent();
                if (byteContent != null) {
                  content = new String(byteContent, myFile.getCharset().name());
                }
              }
              catch (IOException e) {
                LOG.info(e);
              }
              myCachedContents.put(vcsFileRevision.getRevisionNumber(), content);

            }
          }
        }
        finally {
          progressIndicator.popState();
        }

      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process,
                                                                        VcsBundle.message("progress.title.loading.contents"), false, myProject);
    } else {
      process.run();
    }
  }

  public String getContentOf(VcsFileRevision revision) {
    if (! myCachedContents.containsKey(revision.getRevisionNumber())) {
      loadContentsFor(new VcsFileRevision[]{revision});
    }
    return myCachedContents.get(revision.getRevisionNumber());
  }

  private VcsFileRevision[] revisionsNeededToBeLoaded(VcsFileRevision[] revisions) {
    Collection<VcsFileRevision> result = new HashSet<VcsFileRevision>();
    for (VcsFileRevision revision : revisions) {
      result.addAll(collectRevisionsFromFirstTo(revision));
    }

    return result.toArray(new VcsFileRevision[result.size()]);
  }

  private Collection<VcsFileRevision> collectRevisionsFromFirstTo(VcsFileRevision revision) {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    for (VcsFileRevision vcsFileRevision : myRevisions) {
      if (VcsHistoryUtil.compare(revision, vcsFileRevision) > 0) continue;
      result.add(vcsFileRevision);
    }
    return result;
  }
}
