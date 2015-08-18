/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.content.tabs;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public abstract class TabbedContentAction extends AnAction implements DumbAware {

  protected final ContentManager myManager;

  protected final ShadowAction myShadow;

  protected TabbedContentAction(@NotNull final ContentManager manager, @NotNull AnAction shortcutTemplate, @NotNull String text) {
    super(text);
    myManager = manager;
    myShadow = new ShadowAction(this, shortcutTemplate, manager.getComponent(), new Presentation(text));
  }

  protected TabbedContentAction(@NotNull final ContentManager manager, @NotNull AnAction template) {
    myManager = manager;
    myShadow = new ShadowAction(this, template, manager.getComponent());
  }

  public abstract static class ForContent extends TabbedContentAction {

    protected final Content myContent;

    public ForContent(@NotNull Content content, @NotNull AnAction shortcutTemplate, final String text) {
      super(content.getManager(), shortcutTemplate, text);
      myContent = content;
      Disposer.register(content, myShadow);
    }

    public ForContent(@NotNull Content content, final AnAction template) {
      super(content.getManager(), template);
      myContent = content;
      Disposer.register(content, myShadow);
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
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS), UIBundle.message("tabbed.pane.close.all.action.name"));
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
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB));
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
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB));
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
