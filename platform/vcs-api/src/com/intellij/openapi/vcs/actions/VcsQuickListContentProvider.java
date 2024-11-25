package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allow customizing actions in the context dependant 'Vcs Operations' popup.
 *
 * @see VcsQuickListPopupAction
 */
@ApiStatus.OverrideOnly
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
                                       @NotNull AnActionEvent event) {
    return getVcsActions(project, activeVcs, event.getDataContext());
  }

  /**
   * @deprecated Implement {@link #getVcsActions(Project, AbstractVcs, AnActionEvent)}
   * to avoid direct {@link com.intellij.openapi.actionSystem.ActionGroup#getChildren(AnActionEvent)} calls.
   */
  @Nullable
  @Deprecated
  default List<AnAction> getVcsActions(@Nullable Project project,
                                       @NotNull AbstractVcs activeVcs,
                                       @NotNull DataContext dataContext) { return null; }

  /**
   * Customize actions if project is not under VCS.
   * See "Vcs.Operations.Popup.NonVcsAware" action group.
   *
   * @return action list or null if provider should be ignored
   */
  @Nullable
  default List<AnAction> getNotInVcsActions(@Nullable Project project,
                                            @NotNull AnActionEvent event) {
    return getNotInVcsActions(project, event.getDataContext());
  }

  /**
   * @deprecated Implement {@link #getNotInVcsActions(Project, AnActionEvent)}
   * to avoid direct {@link com.intellij.openapi.actionSystem.ActionGroup#getChildren(AnActionEvent)} calls.
   */
  @Nullable
  @Deprecated
  default List<AnAction> getNotInVcsActions(@Nullable Project project,
                                            @NotNull DataContext dataContext) { return null; }

  /**
   * @return True, if only this provider should be used.
   * In this case, default actions and other providers are ignored.
   * Otherwise, custom actions will be inserted at the end.
   * <p>
   * Usually it should be false.
   */
  default boolean replaceVcsActionsFor(@NotNull AbstractVcs activeVcs,
                                       @NotNull DataContext dataContext) { return false; }
}
