package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
public interface VcsQuickListContentProvider {
  ExtensionPointName<VcsQuickListContentProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcsPopupProvider");

  /**
   * Allows to customise VCS actions popup for both custom VCS and general list 
   * @param project Project
   * @param activeVcs Active vcs for current file. Null if context doesn't contain file or vcs is unknown
   * @param dataContext Context
   * @return actions list or null if do nothing
   */
  @Nullable
  List<AnAction> getVcsActions(@Nullable final Project project,
                               @Nullable final AbstractVcs activeVcs,
                               @Nullable final DataContext dataContext);

  /**
   * Allows to customise VCS actions popup if project isn't in VCS
   * @param project Project
   * @param dataContext Context
   * @return actions list or null if do nothing
   */
  @Nullable
  List<AnAction> getNotInVcsActions(@Nullable final Project project,
                                    @Nullable final DataContext dataContext);

  /**
   * @param activeVcs Active vcs for current file
   * @param dataContext Context
   * @return True if replace general actions with actions specified in getVcsActions() method. Otherwise
   * custom actions will be inserted in general popup. Usually should be false.
   */
  boolean replaceVcsActionsFor(@NotNull final AbstractVcs activeVcs,
                               @Nullable final DataContext dataContext);
}
