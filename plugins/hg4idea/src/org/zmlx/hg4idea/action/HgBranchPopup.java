/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.awt.*;

/**
 * <p>
 * The popup which allows to quickly switch and control Hg branches.
 * </p>
 * <p>
 * Use {@link #asListPopup()} to achieve the {@link com.intellij.openapi.ui.popup.ListPopup} itself.
 * </p>
 *
 * @author NadyaZabrodina
 */
public class HgBranchPopup {

  private final Project myProject;

  private final HgRepository myCurrentRepository;
  private final ListPopupImpl myPopup;

  public ListPopup asListPopup() {
    return myPopup;
  }

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   */
  public static HgBranchPopup getInstance(@NotNull Project project, @NotNull HgRepository currentRepository) {
    return new HgBranchPopup(project, currentRepository);
  }

  private HgBranchPopup(@NotNull Project project, @NotNull HgRepository currentRepository) {
    myProject = project;
    myCurrentRepository = currentRepository;
    String title = createPopupTitle(currentRepository);

    Condition<AnAction> preselectActionCondition = new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return false;
      }
    };

    myPopup = new HgBranchActionGroupPopup(title, project, preselectActionCondition);

    setCurrentBranchInfo();
  }


  @NotNull
  private static String createPopupTitle(@NotNull HgRepository currentRepository) {
    String title = "Hg Branches";

    title += " in " + currentRepository.getRoot().getName();

    return title;
  }

  private void setCurrentBranchInfo() {
    String branchText = "Current branch : ";
    myPopup.setAdText(branchText + HgUtil.getDisplayableBranchText(myCurrentRepository), SwingConstants.CENTER);
  }


  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    fillPopupWithCurrentRepositoryActions(popupGroup, createRepositoriesActions());

    popupGroup.addSeparator();
    return popupGroup;
  }


  private DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    for (VirtualFile repository : HgUtil.getHgRepositories(myProject)) {
      HgRepository repo = HgRepositoryImpl.getFullInstance(repository, myProject, myProject);
      popupGroup.add(new RootAction(repo, null));
    }
    return popupGroup;
  }


  private void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new HgBranchPopupActions(myProject, myCurrentRepository).createActions(actions));
  }

  private static class RootAction extends ActionGroup {

    private final HgRepository myRepository;

    /**
     * @param currentRepository Pass null in the case of common repositories - none repository will be highlighted then.
     */
    RootAction(@NotNull HgRepository repository, @Nullable HgRepository currentRepository) {
      super(repository.getRoot().getName(), true);
      myRepository = repository;
      if (repository.equals(currentRepository)) {
        getTemplatePresentation().setIcon(PlatformIcons.CHECK_ICON);
      }
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      ActionGroup group = new HgBranchPopupActions(myRepository.getProject(), myRepository).createActions(null);
      return group.getChildren(e);
    }

    @NotNull
    public String getCaption() {
      String captionText = "Current branch in " + myRepository.getRoot().getName() + ": ";
      return captionText + HgUtil.getDisplayableBranchText(myRepository);
    }

    @NotNull
    public String getBranch() {
      return myRepository.getCurrentBranch();
    }
  }

  private class HgBranchActionGroupPopup extends PopupFactoryImpl.ActionGroupPopup {
    public HgBranchActionGroupPopup(@NotNull String title, @NotNull Project project,
                                    @NotNull Condition<AnAction> preselectActionCondition) {
      super(title, HgBranchPopup.this.createActions(), SimpleDataContext.getProjectContext(project), false, false, false, true, null, -1,
            preselectActionCondition, null);
    }

    @Override
    protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
      WizardPopup popup = super.createPopup(parent, step, parentValue);
      RootAction rootAction = getRootAction(parentValue);
      if (rootAction != null) {
        popup.setAdText((rootAction).getCaption());
      }
      return popup;
    }

    @Nullable
    private RootAction getRootAction(Object value) {
      if (value instanceof PopupFactoryImpl.ActionItem) {
        AnAction action = ((PopupFactoryImpl.ActionItem)value).getAction();
        if (action instanceof RootAction) {
          return (RootAction)action;
        }
      }
      return null;
    }

    @Override
    protected ListCellRenderer getListElementRenderer() {
      return new PopupListElementRenderer(this) {

        private ErrorLabel myBranchLabel;

        @Override
        protected void customizeComponent(JList list, Object value, boolean isSelected) {
          super.customizeComponent(list, value, isSelected);

          RootAction rootAction = getRootAction(value);
          if (rootAction != null) {
            myBranchLabel.setVisible(true);
            myBranchLabel.setText(String.format("[%s]", rootAction.getBranch()));

            if (isSelected) {
              setSelected(myBranchLabel);
            }
            else {
              myBranchLabel.setBackground(getBackground());
              myBranchLabel.setForeground(JBColor.GRAY);    // different foreground than for other elements
            }

            adjustOpacity(myBranchLabel, isSelected);
          }
          else {
            myBranchLabel.setVisible(false);
          }
        }

        @Override
        protected JComponent createItemComponent() {
          myTextLabel = new ErrorLabel();
          myTextLabel.setOpaque(true);
          myTextLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

          myBranchLabel = new ErrorLabel();
          myBranchLabel.setOpaque(true);
          myBranchLabel.setBorder(BorderFactory.createEmptyBorder(1, UIUtil.DEFAULT_HGAP, 1, 1));

          JPanel compoundPanel = new OpaquePanel(new BorderLayout(), Color.white);
          compoundPanel.add(myTextLabel, BorderLayout.CENTER);
          compoundPanel.add(myBranchLabel, BorderLayout.EAST);

          return layoutComponent(compoundPanel);
        }
      };
    }
  }
}

