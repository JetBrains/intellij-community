package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allow customizing actions in the context dependant 'Vcs Operations' popup.
 *
 * @see VcsQuickListPopupAction
 */
public interface VcsQuickListContentProvider {
  ExtensionPointName<VcsQuickListContentProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcsPopupProvider");

  /**
   * Customize actions with specific VCS in the context.
   * See "Vcs.Operations.Popup.VcsAware" action group.
   *
   * @return action list or null if provider should be ignored
   */
  @Nullable
  default List<AnAction> getVcsActions(@Nullable Project project,
                                       @NotNull AbstractVcs activeVcs,
                                       @Nullable DataContext dataContext) { return null; }

  /**
   * Customize actions if project is not under VCS.
   * See "Vcs.Operations.Popup.NonVcsAware" action group.
   *
   * @return action list or null if provider should be ignored
   */
  @Nullable
  default List<AnAction> getNotInVcsActions(@Nullable Project project,
                                            @Nullable DataContext dataContext) { return null; }

  /**
   * @return True, if only this provider should be used.
   * In this case, default actions and other providers are ignored.
   * Otherwise, custom actions will be inserted at the end.
   * <p>
   * Usually it should be false.
   */
  default boolean replaceVcsActionsFor(@NotNull AbstractVcs activeVcs,
                                       @Nullable DataContext dataContext) { return false; }
}
