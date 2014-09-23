/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.preview.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.preview.PreviewInfo;
import com.intellij.openapi.preview.PreviewManager;
import com.intellij.openapi.preview.PreviewPanelProvider;
import com.intellij.openapi.preview.PreviewProviderId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Alarm;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class PreviewManagerImpl implements PreviewManager {
  private static final Key<PreviewInfo> INFO_KEY = Key.create("preview_info");
  private static final int HISTORY_LIMIT = 10;

  private final Project myProject;
  private final Alarm myAlarm = new Alarm();

  private ToolWindowImpl myToolWindow;

  private ContentManager myContentManager;
  private Content myEmptyStateContent;
  private final JPanel myEmptyStatePanel;

  private ArrayList<PreviewInfo> myHistory = new ArrayList<PreviewInfo>();


  private TreeSet<PreviewPanelProvider> myProviders = new TreeSet<PreviewPanelProvider>(new Comparator<PreviewPanelProvider>() {
    @Override
    public int compare(PreviewPanelProvider o1, PreviewPanelProvider o2) {
      return Float.compare(o1.getMenuOrder(), o2.getMenuOrder());
    }
  });
  private Set<PreviewProviderId> myActiveProviderIds = new HashSet<PreviewProviderId>();
  private boolean myInnerSelectionChange;

  private static boolean isAvailable() {
    return UISettings.getInstance().NAVIGATE_TO_PREVIEW;
  }


  public PreviewManagerImpl(Project project) {
    myProject = project;
    myEmptyStatePanel = new EmptyStatePanel();
    PreviewPanelProvider[] providers = PreviewPanelProvider.EP_NAME.getExtensions(project);
    for (PreviewPanelProvider provider : providers) {
      myProviders.add(provider);
      if (provider.shouldBeEnabledByDefault()) {
        myActiveProviderIds.add(provider.getId());
      }
      //todo make it all externalizable to store/restore enabled/disabled state
    }

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        checkGlobalState();
      }
    }, myProject);
    checkGlobalState();
    checkEmptyState();
  }

  @Nullable
  public <V, C> PreviewPanelProvider<V, C> findProvider(@NotNull PreviewProviderId<V, C> id) {
    for (PreviewPanelProvider provider : myProviders) {
      if (id == provider.getId()) return provider;
    }
    return null;
  }


  protected void checkGlobalState() {
    ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(myProject);
    if (!isAvailable() && toolWindowManager.getToolWindow(ToolWindowId.PREVIEW) != null) {
      myHistory.clear();
      myContentManager.removeAllContents(true);
      toolWindowManager.unregisterToolWindow(ToolWindowId.PREVIEW);
      return;
    }
    if (isAvailable() && toolWindowManager.getToolWindow(ToolWindowId.PREVIEW) == null) {
      myToolWindow = (ToolWindowImpl)toolWindowManager
        .registerToolWindow(ToolWindowId.PREVIEW, myEmptyStatePanel, ToolWindowAnchor.RIGHT, myProject, false);
      myContentManager = myToolWindow.getContentManager();
      myToolWindow.setIcon(AllIcons.Actions.PreviewDetails);
      myToolWindow.setContentUiType(ToolWindowContentUiType.COMBO, null);
      myToolWindow.setAutoHide(true);
      myEmptyStateContent = myContentManager.getContent(0);
      myContentManager.addContentManagerListener(new ContentManagerAdapter() {
        @Override
        public void selectionChanged(ContentManagerEvent event) {
          if (myInnerSelectionChange || event.getOperation() != ContentManagerEvent.ContentOperation.add) return;
          PreviewInfo previewInfo = event.getContent().getUserData(INFO_KEY);
          if (previewInfo != null) {
            preview(previewInfo, true);
          }
        }
      });
      MoveToStandardViewAction moveToStandardViewAction = new MoveToStandardViewAction();

      moveToStandardViewAction.registerCustomShortcutSet(new ShortcutSet() {
        @NotNull
        @Override
        public Shortcut[] getShortcuts() {
          Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
          return keymap.getShortcuts("ShowContent");
        }
      }, myToolWindow.getComponent());

      myToolWindow.setTitleActions(moveToStandardViewAction);
      ArrayList<AnAction> myGearActions = new ArrayList<AnAction>();
      for (PreviewPanelProvider provider : myProviders) {
        myGearActions.add(new ContentTypeToggleAction(provider));
      }
      myToolWindow.setAdditionalGearActions(new DefaultActionGroup("Preview", myGearActions));
      //myToolWindow.hide(null);
    }
  }

  private void checkEmptyState() {
    if (myContentManager.getContents().length == 0) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "false");
      myContentManager.addContent(myEmptyStateContent);
      //ApplicationManager.getApplication().invokeLater(new Runnable() {
      //  @Override
      //  public void run() {
      //    if (myContentManager.getIndexOfContent(myEmptyStateContent) != -1) {
      //      toggleToolWindow(false);
      //    }
      //  }
      //});
    }
    else if (myContentManager.getContents().length > 1) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
      myContentManager.removeContent(myEmptyStateContent, false);
    }
  }

  @Nullable
  private Content getContent(@NotNull PreviewInfo info) {
    for (Content content : myContentManager.getContents()) {
      if (info.equals(content.getUserData(INFO_KEY))) {
        return content;
      }
    }
    return null;
  }

  @NotNull
  private Content addContent(PreviewInfo info) {
    myHistory.add(info);
    while (myHistory.size() > HISTORY_LIMIT) {
      PreviewInfo previewInfo = myHistory.remove(0);
      close(previewInfo);
    }

    Content content = myContentManager.getFactory().createContent(info.getComponent(), info.getTitle(), false);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.putUserData(INFO_KEY, info);
    content.setIcon(info.getIcon());
    content.setPopupIcon(info.getIcon());

    myContentManager.addContent(content, 0);
    checkEmptyState();
    return content;
  }

  private void close(@NotNull PreviewInfo info) {
    Content content = getContent(info);
    if (content != null) {
      myContentManager.removeContent(content, false);
      if (myContentManager.getContents().length == 0) {
        toggleToolWindow(false);
      }
      checkEmptyState();
    }
  }

  @Override
  public <V, C> C preview(@NotNull PreviewProviderId<V, C> id, V data, boolean requestFocus) {
    PreviewPanelProvider<V, C> provider = findProvider(id);
    if (provider == null || !myActiveProviderIds.contains(id)) return null;
    return preview(PreviewInfo.create(provider, data), requestFocus);
  }

  public <V, C> C preview(@NotNull PreviewInfo<V, C> info, boolean requestFocus) {
    toggleToolWindow(true);
    Content content = getContent(info);
    if (myContentManager.getSelectedContent() != content) {
      myInnerSelectionChange = true;
      try {
        if (content == null) {
          content = addContent(info);
        }
        myContentManager.setSelectedContent(content);
      }
      finally {
        myInnerSelectionChange = false;
      }
    }
    if (content != null) {
      myContentManager.addContent(content, 0);
    }
    return info.initComponent(requestFocus);
  }

  @Override
  public <V, C> void close(@NotNull PreviewProviderId<V, C> id, V data) {
    for (Content content : myContentManager.getContents()) {
      PreviewInfo info = content.getUserData(INFO_KEY);
      if (info != null && info.getId() == id && info.getData() == data) {
        close(info);
        break;
      }
    }
  }

  @Override
  public <V, C> boolean moveToStandardPlace(@NotNull PreviewProviderId<V, C> id, V data) {
    PreviewPanelProvider<V, C> provider = findProvider(id);
    if (provider != null && provider.moveContentToStandardView(data)) {
      close(id, data);
      return true;
    }
    return false;
  }

  private void toggleToolWindow(boolean activate) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PREVIEW);
    if (toolWindow != null && activate != toolWindow.isActive()) {
      if (activate) {
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            toolWindow.activate(null, false);
          }
        }, 75, ModalityState.stateForComponent(myEmptyStatePanel));
      }
      else {
        if (!myAlarm.isEmpty()) {
          toolWindow.hide(null);
        }
      }
    }
  }

  private class MoveToStandardViewAction extends AnAction {

    public MoveToStandardViewAction() {
      super("Move to standard view", "Move to standard view", AllIcons.Duplicates.SendToTheLeftGrayed);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Content selectedContent = myContentManager.getSelectedContent();
      if (selectedContent == null) return;
      PreviewInfo previewInfo = selectedContent.getUserData(INFO_KEY);
      if (previewInfo != null) {
        moveToStandardPlace(previewInfo.getId(), previewInfo.getData());
        toggleToolWindow(false);
      }
    }
  }

  private class ContentTypeToggleAction extends ToggleAction {
    private final PreviewPanelProvider myProvider;

    ContentTypeToggleAction(PreviewPanelProvider provider) {
      super(provider.getId().getVisualName());
      myProvider = provider;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myActiveProviderIds.contains(myProvider.getId());
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myActiveProviderIds.add(myProvider.getId());
      }
      else {
        myActiveProviderIds.remove(myProvider.getId());
        for (Iterator<PreviewInfo> iterator = myHistory.iterator(); iterator.hasNext(); ) {
          PreviewInfo info = iterator.next();
          if (info.getId().equals(myProvider.getId())) {
            Content content = getContent(info);
            if (content != null) {
              myContentManager.removeContent(content, true);
            }
            iterator.remove();
          }
        }
        checkEmptyState();
      }
    }
  }

  private static class EmptyStatePanel extends JPanel {
    public EmptyStatePanel() {
      setOpaque(true);
    }

    @Override
    public void paint(Graphics g) {
      boolean isDarkBackground = UIUtil.isUnderDarcula();
      UIUtil.applyRenderingHints(g);
      GraphicsUtil.setupAntialiasing(g, true, false);
      g.setColor(new JBColor(isDarkBackground ? Gray._230 : Gray._80, Gray._160));
      g.setFont(UIUtil.getLabelFont().deriveFont(isDarkBackground ? 24f : 20f));

      UIUtil.TextPainter painter = new UIUtil.TextPainter().withLineSpacing(1.5f);
      painter.withShadow(true, new JBColor(Gray._200.withAlpha(100), Gray._0.withAlpha(255)));

      painter.appendLine("No files are open");//.underlined(new JBColor(Gray._150, Gray._180));
      painter.draw(g, new PairFunction<Integer, Integer, Couple<Integer>>() {
        @Override
        public Couple<Integer> fun(Integer width, Integer height) {
          Dimension s = EmptyStatePanel.this.getSize();
          return Couple.of((s.width - width) / 2, (s.height - height) / 2);
        }
      });
    }
  }
}
