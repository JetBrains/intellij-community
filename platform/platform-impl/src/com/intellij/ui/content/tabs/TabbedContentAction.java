// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.tabs;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.wm.impl.content.ContentTabLabel;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
    @NotNull
    protected final Content myContent;

    public ForContent(@NotNull Content content, @NotNull AnAction shortcutTemplate, final @NlsActions.ActionText String text) {
      super(content.getManager(), shortcutTemplate, text, content);

      myContent = content;
    }

    public ForContent(@NotNull Content content, final AnAction template) {
      super(content.getManager(), template, content);

      myContent = content;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myManager.getIndexOfContent(myContent) >= 0);
    }
  }

  @SuppressWarnings("ComponentNotRegistered")
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

  public static class CloseAllButThisAction extends ForContent {
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
      presentation.setEnabledAndVisible(myManager.getContentCount() > 1 && myManager.canCloseAllContents());
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
  }

  public static class MyPreviousTabAction extends TabbedContentAction {
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
  }

  @ApiStatus.Experimental
  public static class SplitTabAction extends TabbedContentAction {
    private final boolean myHorizontal;

    public SplitTabAction(@NotNull ContentManager manager, boolean horizontal) {
      super(manager, new EmptyAction(ActionsBundle.actionText(horizontal ? "MoveTabRight" : "MoveTabDown"), null, null), manager);
      myHorizontal = horizontal;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
      InternalDecoratorImpl decorator = InternalDecoratorImpl.findNearestDecorator(component);
      ContentManager manager = ObjectUtils.notNull(e.getData(PlatformDataKeys.CONTENT_MANAGER), myManager);
      Content content = ObjectUtils.chooseNotNull(ObjectUtils.doIfCast(component, ContentTabLabel.class, o -> o.getContent()),
                                                  manager.getSelectedContent());
      if (content != null) {
        decorator.splitWithContent(content, myHorizontal ? SwingConstants.RIGHT : SwingConstants.BOTTOM, -1);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      ObjectUtils.consumeIfNotNull(ActionUtil.getAction(myHorizontal ? "SplitVertically" : "SplitHorizontally"),
                                   action -> e.getPresentation().setIcon(action.getTemplatePresentation().getIcon()));
      e.getPresentation().setEnabledAndVisible(myManager.getContents().length > 1);
    }
  }

  @ApiStatus.Experimental
  public static class UnsplitTabAction extends TabbedContentAction {
    public UnsplitTabAction(@NotNull ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction("Unsplit"), manager);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InternalDecoratorImpl decorator = InternalDecoratorImpl.findNearestDecorator(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
      decorator.unsplit(myManager.getSelectedContent());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      InternalDecoratorImpl decorator = InternalDecoratorImpl.findNearestDecorator(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
      e.getPresentation().setEnabledAndVisible(decorator != null && decorator.canUnsplit());
      e.getPresentation().setText(ActionsBundle.actionText("Unsplit"));
    }
  }
}
