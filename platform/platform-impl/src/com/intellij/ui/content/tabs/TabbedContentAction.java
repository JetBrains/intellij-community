// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.tabs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public abstract class TabbedContentAction extends AnAction implements DumbAware {
  protected final ContentManager myManager;

  protected final ShadowAction myShadow;

  protected TabbedContentAction(@NotNull ContentManager manager, @NotNull AnAction shortcutTemplate, @NotNull String text, @NotNull Disposable parentDisposable) {
    super(text);
    myManager = manager;
    myShadow = new ShadowAction(this, shortcutTemplate, manager.getComponent(), new Presentation(text), parentDisposable);
  }

  protected TabbedContentAction(@NotNull ContentManager manager, @NotNull AnAction template, @NotNull Disposable parentDisposable) {
    myManager = manager;
    myShadow = new ShadowAction(this, template, manager.getComponent(), parentDisposable);
  }

  public abstract static class ForContent extends TabbedContentAction {
    protected final Content myContent;

    public ForContent(@NotNull Content content, @NotNull AnAction shortcutTemplate, final String text) {
      super(content.getManager(), shortcutTemplate, text, content);

      myContent = content;
    }

    public ForContent(@NotNull Content content, final AnAction template) {
      super(content.getManager(), template, content);

      myContent = content;
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myManager.getIndexOfContent(myContent) >= 0);
    }
  }

  public static class CloseAction extends ForContent {
    public CloseAction(@NotNull Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.removeContent(myContent, true);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(myManager.canCloseContents() && myContent.isCloseable());
      presentation.setText(myManager.getCloseActionName());
    }
  }

  public static class CloseAllButThisAction extends ForContent {
    public CloseAllButThisAction(@NotNull Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS_BUT_THIS), UIBundle.message("tabbed.pane.close.all.but.this.action.name"));
    }

    public void actionPerformed(AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (myContent != content && content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
      myManager.setSelectedContent(myContent);
    }

    public void update(AnActionEvent e) {
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

  public static class CloseAllAction extends TabbedContentAction {
    public CloseAllAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS), UIBundle.message("tabbed.pane.close.all.action.name"), manager);
    }

    public void actionPerformed(AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(myManager.getContentCount() > 1 && myManager.canCloseAllContents());
    }
  }
  public static class MyNextTabAction extends TabbedContentAction {
    public MyNextTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB), manager);
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectNextContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myManager.getContentCount() > 1);
      e.getPresentation().setText(myManager.getNextContentActionName());
    }
  }

  public static class MyPreviousTabAction extends TabbedContentAction {
    public MyPreviousTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB), manager);
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectPreviousContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myManager.getContentCount() > 1);
      e.getPresentation().setText(myManager.getPreviousContentActionName());
    }
  }
}
