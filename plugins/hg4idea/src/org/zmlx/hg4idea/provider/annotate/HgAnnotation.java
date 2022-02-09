// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgVcs;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class HgAnnotation extends FileAnnotation {

  private StringBuilder myContentBuffer;

  public enum FIELD {
    USER, REVISION, DATE, LINE, CONTENT
  }

  private final HgLineAnnotationAspect dateAnnotationAspect = new HgLineAnnotationAspect(FIELD.DATE);
  private final HgLineAnnotationAspect userAnnotationAspect = new HgLineAnnotationAspect(FIELD.USER);
  private final HgLineAnnotationAspect revisionAnnotationAspect = new HgLineAnnotationAspect(FIELD.REVISION);

  @NotNull private final Project myProject;
  @NotNull private final List<? extends HgAnnotationLine> myLines;
  @NotNull private final List<? extends HgFileRevision> myFileRevisions;
  @NotNull private final HgFile myFile;
  private final VcsRevisionNumber myCurrentRevision;

  public HgAnnotation(@NotNull Project project, @NotNull HgFile hgFile, @NotNull List<? extends HgAnnotationLine> lines,
                      @NotNull List<? extends HgFileRevision> vcsFileRevisions, VcsRevisionNumber revision) {
    super(project);
    myProject = project;
    myLines = lines;
    myFileRevisions = vcsFileRevisions;
    myFile = hgFile;
    myCurrentRevision = revision;
  }

  @Override
  public int getLineCount() {
    return myLines.size();
  }

  @Override
  public void dispose() {
  }

  @Override
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{
      revisionAnnotationAspect,
      dateAnnotationAspect,
      userAnnotationAspect
    };
  }

  @Nullable
  @Override
  public String getToolTip(int lineNumber) {
    return getToolTip(lineNumber, false);
  }

  @Nullable
  @Override
  public String getHtmlToolTip(int lineNumber) {
    return getToolTip(lineNumber, true);
  }

  @Nls
  @Nullable
  private String getToolTip(int lineNumber, boolean asHtml) {
    if (myLines.size() <= lineNumber || lineNumber < 0) {
      return null;
    }
    HgAnnotationLine info = myLines.get(lineNumber);
    if (info == null) {
      return null;
    }

    HgFileRevision revision = ContainerUtil.find(myFileRevisions, it -> it.getRevisionNumber().equals(info.getVcsRevisionNumber()));
    if (revision == null) return null;

    AnnotationTooltipBuilder atb = new AnnotationTooltipBuilder(myProject, asHtml);
    atb.appendRevisionLine(revision.getRevisionNumber(), null);
    atb.appendLine(HgBundle.message("hg4idea.annotation.author", revision.getAuthor()));
    atb.appendLine(HgBundle.message("hg4idea.annotation.date", revision.getRevisionDate()));
    String message = revision.getCommitMessage();
    if (message != null) atb.appendCommitMessageBlock(message);
    return atb.toString();
  }

  @Override
  public String getAnnotatedContent() {
    if (myContentBuffer == null) {
      myContentBuffer = new StringBuilder();
      for (int i = 0; i < myLines.size(); i++) {
        HgAnnotationLine line = myLines.get(i);
        if (i > 0) myContentBuffer.append("\n");
        myContentBuffer.append(line.get(FIELD.CONTENT));
      }
    }
    return myContentBuffer.toString();
  }

  @Override
  @Nullable
  public VcsRevisionNumber getLineRevisionNumber(int lineNumber) {
    if (lineNumber >= myLines.size() || lineNumber < 0) {
      return null;
    }
    HgAnnotationLine annotationLine = myLines.get(lineNumber);
    return annotationLine.getVcsRevisionNumber();
  }

  @Override
  @Nullable
  public Date getLineDate(int lineNumber) {
    //if (lineNumber >= myLines.size() || lineNumber < 0) {
    //  return null;
    //}
    //lines.get(lineNumber).get(HgAnnotation.FIELD.DATE)
    // todo : parse date
    return null;
  }

  @Override
  @Nullable
  public List<VcsFileRevision> getRevisions() {
    List<VcsFileRevision> result = new LinkedList<>();
    result.addAll(myFileRevisions);
    return result;
  }

  @Nullable
  private static String id(FIELD field) {
    switch (field) {
      case USER:
        return LineAnnotationAspect.AUTHOR;
      case REVISION:
        return LineAnnotationAspect.REVISION;
      case DATE:
        return LineAnnotationAspect.DATE;
      default:
        return null;
    }
  }

  private static @NlsContexts.ListItem @Nullable String displayName(FIELD field) {
    switch (field) {
      case USER:
        return VcsBundle.message("line.annotation.aspect.author");
      case REVISION:
        return VcsBundle.message("line.annotation.aspect.revision");
      case DATE:
        return VcsBundle.message("line.annotation.aspect.date");
      default:
        return null;
    }
  }

  private static boolean isShowByDefault(FIELD aspectType) {
    return aspectType == FIELD.DATE || aspectType == FIELD.USER;
  }

  private class HgLineAnnotationAspect extends LineAnnotationAspectAdapter {
    private final FIELD myAspectType;

    HgLineAnnotationAspect(FIELD aspectType) {
      super(id(aspectType), HgAnnotation.displayName(aspectType), HgAnnotation.isShowByDefault(aspectType));
      myAspectType = aspectType;
    }

    @Override
    public String getValue(int lineNumber) {
      if (lineNumber >= myLines.size() || lineNumber < 0) {
        return "";
      }
      HgAnnotationLine annotationLine = myLines.get(lineNumber);
      return myAspectType == FIELD.REVISION
             ? annotationLine.getVcsRevisionNumber().asString()
             : annotationLine.get(myAspectType).toString();
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (lineNum >= 0 && lineNum < myLines.size()) {
        HgAnnotationLine line = myLines.get(lineNum);
        VirtualFile file = myFile.toFilePath().getVirtualFile();
        if (line != null && file != null) {
          ShowAllAffectedGenericAction.showSubmittedFiles(myProject, line.getVcsRevisionNumber(), file, HgVcs.getKey());
        }
      }
    }
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return myCurrentRevision;
  }

  @Override
  public VcsKey getVcsKey() {
    return HgVcs.getKey();
  }

  @Override
  public VirtualFile getFile() {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myFile.getFile());
  }

  @Nullable
  @Override
  public LineModificationDetailsProvider getLineModificationDetailsProvider() {
    return DefaultLineModificationDetailsProvider.create(this);
  }
}
