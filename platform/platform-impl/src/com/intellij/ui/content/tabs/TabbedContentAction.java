// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.tabs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.wm.impl.content.ContentTabLabel;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class TabbedContentAction extends AnAction implements DumbAware {
  protected final ContentManager myManager;

  protected final ShadowAction myShadow;

  protected TabbedContentAction(@NotNull ContentManager manager,
                                @NotNull AnAction shortcutTemplate,
                                @NotNull @NlsActions.ActionText String text,
                                @NotNull Disposable parentDisposable) {
    super(text);

    myManager = manager;
    myShadow = new ShadowAction(this, shortcutTemplate, manager.getComponent(), new Presentation(text), parentDisposable);
  }

  protected TabbedContentAction(@NotNull ContentManager manager, @NotNull AnAction template, @NotNull Disposable parentDisposable) {
    myManager = manager;
    myShadow = new ShadowAction(this, template, manager.getComponent(), parentDisposable);
  }

  public abstract static class ForContent extends TabbedContentAction {
    protected final @NotNull Content myContent;

    public ForContent(@NotNull Content content, @NotNull AnAction shortcutTemplate, final @NlsActions.ActionText String text) {
      super(content.getManager(), shortcutTemplate, text, content);

      myContent = content;
    }

    public ForContent(@NotNull Content content, final AnAction template) {
      super(content.getManager(), template, content);

      myContent = content;
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myManager.getIndexOfContent(myContent) >= 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  public static class CloseAction extends ForContent {
    public CloseAction(@NotNull Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myManager.removeContent(myContent, true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(myManager.canCloseContents() && myContent.isCloseable());
      presentation.setText(myManager.getCloseActionName());
    }
  }

  public static final class CloseAllButThisAction extends ForContent {
    public CloseAllButThisAction(@NotNull Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS_BUT_THIS),
            UIBundle.message("tabbed.pane.close.all.but.this.action.name"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (myContent != content && content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
      myManager.setSelectedContent(myContent);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(myManager.getCloseAllButThisActionName());
      presentation.setEnabledAndVisible(myManager.canCloseContents() && hasOtherCloseableContents());
    }

    private boolean hasOtherCloseableContents() {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (myContent != content && content.isCloseable()) {
          return true;
        }
      }
      return false;
    }
  }

  public static final class CloseAllAction extends TabbedContentAction {
    public CloseAllAction(@NotNull ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS),
            UIBundle.message("tabbed.pane.close.all.action.name"), manager);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      for (Content content : myManager.getContents()) {
        if (content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      boolean notForTheOnlyContent = myManager.getContentCount() > 1 || !(component instanceof ContentTabLabel);
      presentation.setEnabledAndVisible(notForTheOnlyContent && myManager.canCloseAllContents());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  public static final class MyNextTabAction extends TabbedContentAction {
    public MyNextTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB), manager);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myManager.selectNextContent();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myManager.getContentCount() > 1);
      e.getPresentation().setText(myManager.getNextContentActionName());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  public static final class MyPreviousTabAction extends TabbedContentAction {
    public MyPreviousTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB), manager);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myManager.selectPreviousContent();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myManager.getContentCount() > 1);
      e.getPresentation().setText(myManager.getPreviousContentActionName());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
