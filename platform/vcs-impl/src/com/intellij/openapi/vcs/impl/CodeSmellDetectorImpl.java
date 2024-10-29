// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.MainPassesRunner;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CodeSmellDetector;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class CodeSmellDetectorImpl extends CodeSmellDetector {
  private static final Key<Boolean> CODE_SMELL_DETECTOR_KEY = new Key<Boolean>("CODE_SMELL_DETECTOR_KEY");

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(CodeSmellDetectorImpl.class);

  public CodeSmellDetectorImpl(final Project project) {
    myProject = project;
  }

  @Override
  public void showCodeSmellErrors(@NotNull final List<CodeSmellInfo> smellList) {
    smellList.sort(Comparator.comparingInt(o -> o.getTextRange().getStartOffset()));

    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (smellList.isEmpty()) {
        return;
      }

      final VcsErrorViewPanel errorTreeView = new VcsErrorViewPanel(myProject);

      FileDocumentManager fileManager = FileDocumentManager.getInstance();

      for (CodeSmellInfo smellInfo : smellList) {
        final VirtualFile file = fileManager.getFile(smellInfo.getDocument());
        if (file == null) continue;
        String presentableUrl = file.getPresentableUrl();
        final OpenFileDescriptor navigatable =
          new OpenFileDescriptor(myProject, file, smellInfo.getStartLine(), smellInfo.getStartColumn());
        final String exportPrefix = NewErrorTreeViewPanel.createExportPrefix(smellInfo.getStartLine() + 1);
        final String rendererPrefix =
          NewErrorTreeViewPanel.createRendererPrefix(smellInfo.getStartLine() + 1, smellInfo.getStartColumn() + 1);
        if (smellInfo.getSeverity() == HighlightSeverity.ERROR) {
          errorTreeView.addMessage(MessageCategory.ERROR, new String[]{smellInfo.getDescription()}, FileUtil.getLocationRelativeToUserHome(presentableUrl), navigatable,
                                   exportPrefix, rendererPrefix, null);
        }
        else {//if (smellInfo.getSeverity() == HighlightSeverity.WARNING) {
          errorTreeView.addMessage(MessageCategory.WARNING, new String[]{smellInfo.getDescription()}, FileUtil.getLocationRelativeToUserHome(presentableUrl),
                                   navigatable, exportPrefix, rendererPrefix, null);
        }

      }

      ToolWindow toolWindow = ProblemsView.getToolWindow(myProject);
      if (toolWindow != null && toolWindow.isAvailable()) {
        toolWindow.activate(() -> {
          ContentManager contentManager = toolWindow.getContentManager();

          for (Content oldContent : contentManager.getContents()) {
            if (oldContent.isPinned()) continue;
            if (Boolean.TRUE.equals(oldContent.getUserData(CODE_SMELL_DETECTOR_KEY))) {
              contentManager.removeContent(oldContent, true);
            }
          }

          ContentImpl content = new ContentImpl(errorTreeView, VcsBundle.message("code.smells.error.messages.tab.name"), true);
          content.putUserData(CODE_SMELL_DETECTOR_KEY, true);
          contentManager.addContent(content);
          contentManager.setSelectedContent(content, true);
        }, true, true);
      }
      else {
        AbstractVcsHelperImpl helper = (AbstractVcsHelperImpl)AbstractVcsHelper.getInstance(myProject);
        helper.openMessagesView(errorTreeView, VcsBundle.message("code.smells.error.messages.tab.name"));
      }
    });
  }

  @NotNull
  @Override
  public List<CodeSmellInfo> findCodeSmells(@NotNull final List<? extends VirtualFile> filesToCheck) throws ProcessCanceledException {
    MainPassesRunner runner =
      new MainPassesRunner(myProject, VcsBundle.message("checking.code.smells.progress.title"), getInspectionProfile());
    Map<Document, List<HighlightInfo>> infos = runner.runMainPasses(filesToCheck, HighlightSeverity.WARNING);
    return convertErrorsAndWarnings(infos);
  }
  @Nullable
  private InspectionProfile getInspectionProfile() {
    InspectionProfile currentProfile;
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    String codeSmellProfile = vcsConfiguration.CODE_SMELLS_PROFILE;
    if (codeSmellProfile != null) {
      currentProfile = (vcsConfiguration.CODE_SMELLS_PROFILE_LOCAL ? InspectionProfileManager.getInstance() : InspectionProjectProfileManager.getInstance(
        myProject)).getProfile(codeSmellProfile);
    }
    else {
      currentProfile = null;
    }
    return currentProfile;
  }

  private @NotNull List<CodeSmellInfo> convertErrorsAndWarnings(@NotNull Map<Document, List<HighlightInfo>> highlights) {
    List<CodeSmellInfo> result = new ArrayList<>();
    for (Map.Entry<Document, List<HighlightInfo>> e : highlights.entrySet()) {
      Document document = e.getKey();
      List<HighlightInfo> infos = e.getValue();
      for (HighlightInfo info : infos) {
        final HighlightSeverity severity = info.getSeverity();
        if (SeverityRegistrar.getSeverityRegistrar(myProject).compare(severity, HighlightSeverity.WARNING) >= 0) {
            result.add(new CodeSmellInfo(document, getDescription(info),
                                         new TextRange(info.startOffset, info.endOffset), severity));
        }
      }
    }
    return result;
  }

  private static String getDescription(@NotNull HighlightInfo highlightInfo) {
    final String description = highlightInfo.getDescription();
    final HighlightInfoType type = highlightInfo.type;
    if (type instanceof HighlightInfoType.HighlightInfoTypeSeverityByKey) {
      final HighlightDisplayKey severityKey = ((HighlightInfoType.HighlightInfoTypeSeverityByKey)type).getSeverityKey();
      final String id = severityKey.getID();
      return "[" + id + "] " + description;
    }
    return description;
  }
}
