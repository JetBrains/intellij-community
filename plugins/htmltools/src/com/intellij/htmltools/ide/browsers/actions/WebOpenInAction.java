package com.intellij.htmltools.ide.browsers.actions;

import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public final class WebOpenInAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    ActionGroup group = getGroup();
    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(HtmlToolsBundle.message("html.action.open_in.list.popup.title"), group, dataContext,
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false);
    Object onlyItem = ContainerUtil.getOnlyItem(popup.getListStep().getValues());
    if (onlyItem instanceof PopupFactoryImpl.ActionItem actionItem) {
      actionItem.getAction().actionPerformed(e);
    }
    else {
      popup.showInBestPositionFor(dataContext);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    AnAction onlyAction = ActionGroupUtil.getSingleActiveAction(getGroup(), event);
    presentation.setText(onlyAction != null
                         ? HtmlToolsBundle.message("html.action.open_in.list.prefix") + " " + onlyAction.getTemplatePresentation().getText()
                         : HtmlToolsBundle.message("html.action.open_in.list.popup.title"));
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(), event)); //
  }

  private static ActionGroup getGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("OpenInBrowserEditorContextBarGroupAction");
  }
}
