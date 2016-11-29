/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public abstract class StickyHeadGetter {
  protected final String myStickyData;
  private final Project myProject;

  protected StickyHeadGetter(final String stickyData, Project project) {
    myStickyData = stickyData;
    myProject = project;
  }

  public static class MyStickyBranchHeadGetter extends StickyHeadGetter {
    public MyStickyBranchHeadGetter(final String headRevision, Project project) {
      super(headRevision, project);
    }

    @Override
    public String getHead(final VirtualFile parent, final String name) {
      final String branchRoot = getTagStart(myStickyData);
      if (branchRoot == null) return myStickyData;

      return getBranchHeadRevision(parent, name, new Convertor<CvsRevisionNumber, Boolean>() {
        public Boolean convert(CvsRevisionNumber o) {
          return o.asString().startsWith(branchRoot);
        }
      });
    }
  }

  @Nullable
  private static String getTagStart(final String currentRevision) {
    final int[] subRevisions = new CvsRevisionNumber(currentRevision).getSubRevisions();
    if (subRevisions == null || subRevisions.length < 2) return null;

    final int[] top = new int[subRevisions.length - 1];
    System.arraycopy(subRevisions, 0, top, 0, subRevisions.length - 1);
    return StringUtil.join(top, ".");
  }

  public static class MyStickyTagGetter extends StickyHeadGetter {
    public MyStickyTagGetter(final String stickyData, Project project) {
      super(stickyData, project);
    }

    @Override
    public String getHead(final VirtualFile parent, final String name) {
      return myStickyData;
    }
  }

  public static class MyStickyDateGetter extends StickyHeadGetter {
    private final Date myStickyDate;
    private final String myTagStart;

    public MyStickyDateGetter(String stickyData, final Date stickyDate, final String currentRevision, Project project) {
      super(stickyData, project);
      myStickyDate = stickyDate;
      myTagStart = getTagStart(currentRevision);
    }

    @Override
    public String getHead(final VirtualFile parent, final String name) {
      if (myTagStart == null) return myStickyData;
      return getBranchHeadRevision(parent, name, null);
    }

    @Override
    protected String extractRevision(LocalPathIndifferentLogOperation operation, Convertor<CvsRevisionNumber, Boolean> chooser) {
      final List<LogInformation> informations = operation.getLogInformationList();
      for (LogInformation information : informations) {
        final List<Revision> revisionList = information.getRevisionList();
        for (Revision revision : revisionList) {
          if (revision.getNumber().startsWith(myTagStart)) {
            if (Comparing.compare(revision.getDate(), myStickyDate) <= 0) {
              return revision.getNumber();
            }
          }
        }
      }
      return myStickyData;
    }                                           
  }

  public abstract String getHead(final VirtualFile parent, final String name);

  @Nullable
  protected String getBranchHeadRevision(final VirtualFile parent,
                                       final String name,
                                       final Convertor<CvsRevisionNumber, Boolean> chooser) {
    final LocalPathIndifferentLogOperation operation = new LocalPathIndifferentLogOperation(new File(parent.getPath(), name));
    final Ref<Boolean> logSuccess = new Ref<>(Boolean.TRUE);
    final CvsExecutionEnvironment cvsExecutionEnvironment = new CvsExecutionEnvironment(new CvsMessagesAdapter(),
      CvsExecutionEnvironment.DUMMY_STOPPER, new ErrorProcessor() {
      public void addError(VcsException ex) {
        logSuccess.set(Boolean.FALSE);
      }
      public List<VcsException> getErrors() {
        return null;
      }
    }, PostCvsActivity.DEAF, myProject);
    try {
      // should already be logged in
      //operation.login(context);
      operation.execute(cvsExecutionEnvironment, false);
    }
    catch (VcsException e) {
      //
    }
    catch (CommandAbortedException e) {
      //
    }
    if (Boolean.TRUE.equals(logSuccess.get())) {
      return extractRevision(operation, chooser);
    }
    return null;
  }

  @Nullable
  protected String extractRevision(final LocalPathIndifferentLogOperation operation, final Convertor<CvsRevisionNumber, Boolean> chooser) {
    final Collection<CvsRevisionNumber> numberCollection = operation.getAllRevisions();
    if (numberCollection == null) return null;

    for (CvsRevisionNumber revisionNumber : numberCollection) {
      final String stringPresentation = revisionNumber.asString();
      if (chooser.convert(revisionNumber)) {
        return stringPresentation;
      }
    }
    return null;
  }
}
