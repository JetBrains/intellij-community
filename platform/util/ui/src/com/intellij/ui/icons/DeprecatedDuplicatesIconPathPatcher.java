// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.util.IconPathPatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("SpellCheckingInspection")
final class DeprecatedDuplicatesIconPathPatcher extends IconPathPatcher {
  private static final @NonNls Map<String, String> deprecatedIconsReplacements;

  static {
    Map<String, String> map = new HashMap<>();
    map.put("/actions/prevfile.png", "AllIcons.Actions.Back");
    map.put("/actions/prevfile.svg", "AllIcons.Actions.Back");

    map.put("/actions/delete.png", "AllIcons.Actions.Cancel");
    map.put("/actions/delete.svg", "AllIcons.Actions.Cancel");

    map.put("/actions/checkedBlack.png", "AllIcons.Actions.Checked");
    map.put("/actions/checkedBlack.svg", "AllIcons.Actions.Checked");
    map.put("/actions/checkedGrey.png", "AllIcons.Actions.Checked");
    map.put("/actions/checkedGrey.svg", "AllIcons.Actions.Checked");

    map.put("/general/collapseAll.png", "AllIcons.Actions.Collapseall");
    map.put("/general/collapseAll.svg", "AllIcons.Actions.Collapseall");
    map.put("/general/collapseAllHover.png", "AllIcons.Actions.Collapseall");
    map.put("/general/collapseAllHover.svg", "AllIcons.Actions.Collapseall");

    map.put("/actions/get.png", "AllIcons.Actions.Download");
    map.put("/actions/get.svg", "AllIcons.Actions.Download");

    map.put("/modules/edit.png", "AllIcons.Actions.Edit");
    map.put("/modules/edit.svg", "AllIcons.Actions.Edit");
    map.put("/toolbarDecorator/edit.png", "AllIcons.Actions.Edit");
    map.put("/toolbarDecorator/edit.svg", "AllIcons.Actions.Edit");
    map.put("/toolbarDecorator/mac/edit.png", "AllIcons.Actions.Edit");
    map.put("/toolbarDecorator/mac/edit.svg", "AllIcons.Actions.Edit");

    map.put("/general/expandAll.png", "AllIcons.Actions.Expandall");
    map.put("/general/expandAll.svg", "AllIcons.Actions.Expandall");
    map.put("/general/expandAllHover.png", "AllIcons.Actions.Expandall");
    map.put("/general/expandAllHover.svg", "AllIcons.Actions.Expandall");

    map.put("/actions/findPlain.png", "AllIcons.Actions.Find");
    map.put("/actions/findPlain.svg", "AllIcons.Actions.Find");
    map.put("/actions/menu_find.png", "AllIcons.Actions.Find");
    map.put("/actions/menu_find.svg", "AllIcons.Actions.Find");

    map.put("/actions/nextfile.png", "AllIcons.Actions.Forward");
    map.put("/actions/nextfile.svg", "AllIcons.Actions.Forward");
    map.put("/hierarchy/base.png", "AllIcons.Actions.Forward");
    map.put("/hierarchy/base.svg", "AllIcons.Actions.Forward");

    map.put("/objectBrowser/showModules.png", "AllIcons.Actions.GroupByModule");
    map.put("/objectBrowser/showModules.svg", "AllIcons.Actions.GroupByModule");

    map.put("/toolbar/folders.png", "AllIcons.Actions.GroupByPackage");
    map.put("/toolbar/folders.svg", "AllIcons.Actions.GroupByPackage");

    map.put("/actions/menu_help.png", "AllIcons.Actions.Help");
    map.put("/actions/menu_help.svg", "AllIcons.Actions.Help");
    map.put("/debugger/readHelp.png", "AllIcons.Actions.Help");
    map.put("/debugger/readHelp.svg", "AllIcons.Actions.Help");
    map.put("/runConfigurations/unknown.png", "AllIcons.Actions.Help");
    map.put("/runConfigurations/unknown.svg", "AllIcons.Actions.Help");

    map.put("/actions/createFromUsage.png", "AllIcons.Actions.IntentionBulb");
    map.put("/actions/createFromUsage.svg", "AllIcons.Actions.IntentionBulb");

    map.put("/runConfigurations/variables.png", "AllIcons.Actions.ListFiles");
    map.put("/runConfigurations/variables.svg", "AllIcons.Actions.ListFiles");

    map.put("/general/openProject.png", "AllIcons.Actions.MenuOpen");
    map.put("/general/openProject.svg", "AllIcons.Actions.MenuOpen");
    map.put("/welcome/openProject.png", "AllIcons.Actions.MenuOpen");
    map.put("/welcome/openProject.svg", "AllIcons.Actions.MenuOpen");

    map.put("/debugger/threadStates/io.png", "AllIcons.Actions.MenuSaveall");
    map.put("/debugger/threadStates/io.svg", "AllIcons.Actions.MenuSaveall");
    map.put("/runConfigurations/saveTempConfig.png", "AllIcons.Actions.MenuSaveall");
    map.put("/runConfigurations/saveTempConfig.svg", "AllIcons.Actions.MenuSaveall");


    map.put("/actions/sortDesc.png", "AllIcons.Actions.MoveDown");
    map.put("/actions/sortDesc.svg", "AllIcons.Actions.MoveDown");
    map.put("/toolbarDecorator/moveDown.png", "AllIcons.Actions.MoveDown");
    map.put("/toolbarDecorator/moveDown.svg", "AllIcons.Actions.MoveDown");
    map.put("/toolbarDecorator/mac/moveDown.png", "AllIcons.Actions.MoveDown");
    map.put("/toolbarDecorator/mac/moveDown.svg", "AllIcons.Actions.MoveDown");

    map.put("/actions/moveToStandardPlace.png", "AllIcons.Actions.MoveTo2");
    map.put("/actions/moveToStandardPlace.svg", "AllIcons.Actions.MoveTo2");

    map.put("/actions/sortAsc.png", "AllIcons.Actions.MoveUp");
    map.put("/actions/sortAsc.svg", "AllIcons.Actions.MoveUp");
    map.put("/toolbarDecorator/moveUp.png", "AllIcons.Actions.MoveUp");
    map.put("/toolbarDecorator/moveUp.svg", "AllIcons.Actions.MoveUp");
    map.put("/toolbarDecorator/mac/moveUp.png", "AllIcons.Actions.MoveUp");
    map.put("/toolbarDecorator/mac/moveUp.svg", "AllIcons.Actions.MoveUp");

    map.put("/debugger/stackFrame.png", "AllIcons.Debugger.Frame");
    map.put("/debugger/stackFrame.svg", "AllIcons.Debugger.Frame");

    map.put("/debugger/threadStates/paused.png", "AllIcons.Actions.Pause");
    map.put("/debugger/threadStates/paused.svg", "AllIcons.Actions.Pause");

    map.put("/actions/showSource.png", "AllIcons.Actions.Preview");
    map.put("/actions/showSource.svg", "AllIcons.Actions.Preview");

    map.put("/actions/browser-externalJavaDoc.png", "AllIcons.Actions.PreviousOccurence");
    map.put("/actions/browser-externalJavaDoc.svg", "AllIcons.Actions.PreviousOccurence");

    map.put("/actions/sync.png", "AllIcons.Actions.Refresh");
    map.put("/actions/sync.svg", "AllIcons.Actions.Refresh");
    map.put("/actions/synchronizeFS.png", "AllIcons.Actions.Refresh");
    map.put("/actions/synchronizeFS.svg", "AllIcons.Actions.Refresh");
    map.put("/vcs/refresh.png", "AllIcons.Actions.Refresh");
    map.put("/vcs/refresh.svg", "AllIcons.Actions.Refresh");

    map.put("/actions/menu_replace.png", "AllIcons.Actions.Replace");
    map.put("/actions/menu_replace.svg", "AllIcons.Actions.Replace");

    map.put("/actions/refreshUsages.png", "AllIcons.Actions.Rerun");
    map.put("/actions/refreshUsages.svg", "AllIcons.Actions.Rerun");

    map.put("/debugger/threadStates/running.png", "AllIcons.Actions.Resume");
    map.put("/debugger/threadStates/running.svg", "AllIcons.Actions.Resume");

    map.put("/actions/reset.png", "AllIcons.Actions.Rollback");
    map.put("/actions/reset.svg", "AllIcons.Actions.Rollback");

    map.put("/general/recursive.png", "AllIcons.Actions.ShowAsTree");
    map.put("/general/recursive.svg", "AllIcons.Actions.ShowAsTree");
    map.put("/vcs/mergeSourcesTree.png", "AllIcons.Actions.ShowAsTree");
    map.put("/vcs/mergeSourcesTree.svg", "AllIcons.Actions.ShowAsTree");

    map.put("/actions/submit1.png", "AllIcons.Actions.SetDefault");
    map.put("/actions/submit1.svg", "AllIcons.Actions.SetDefault");

    map.put("/general/debug.png", "AllIcons.Actions.StartDebugger");
    map.put("/general/debug.svg", "AllIcons.Actions.StartDebugger");


    map.put("/codeStyle/mac/AddNewSectionRule.png", "AllIcons.CodeStyle.AddNewSectionRule");
    map.put("/codeStyle/mac/AddNewSectionRule.svg", "AllIcons.CodeStyle.AddNewSectionRule");


    map.put("/debugger/watches.png", "AllIcons.Debugger.Watch");
    map.put("/debugger/watches.svg", "AllIcons.Debugger.Watch");


    map.put("/debugger/newWatch.png", "AllIcons.General.Add");
    map.put("/debugger/newWatch.svg", "AllIcons.General.Add");
    map.put("/modules/addContentEntry.png", "AllIcons.General.Add");
    map.put("/modules/addContentEntry.svg", "AllIcons.General.Add");
    map.put("/toolbarDecorator/add.png", "AllIcons.General.Add");
    map.put("/toolbarDecorator/add.svg", "AllIcons.General.Add");
    map.put("/toolbarDecorator/mac/add.png", "AllIcons.General.Add");
    map.put("/toolbarDecorator/mac/add.svg", "AllIcons.General.Add");

    map.put("/runConfigurations/configurationWarning.png", "AllIcons.General.BalloonError");
    map.put("/runConfigurations/configurationWarning.svg", "AllIcons.General.BalloonError");

    map.put("/compiler/error.png", "AllIcons.General.Error");
    map.put("/compiler/error.svg", "AllIcons.General.Error");
    map.put("/ide/errorSign.png", "AllIcons.General.Error");
    map.put("/ide/errorSign.svg", "AllIcons.General.Error");

    map.put("/general/externalToolsSmall.png", "AllIcons.General.ExternalTools");
    map.put("/general/externalToolsSmall.svg", "AllIcons.General.ExternalTools");

    map.put("/actions/filter_small.png", "AllIcons.General.Filter");
    map.put("/actions/filter_small.svg", "AllIcons.General.Filter");
    map.put("/ant/filter.png", "AllIcons.General.Filter");
    map.put("/ant/filter.svg", "AllIcons.General.Filter");
    map.put("/debugger/class_filter.png", "AllIcons.General.Filter");
    map.put("/debugger/class_filter.svg", "AllIcons.General.Filter");
    map.put("/inspector/useFilter.png", "AllIcons.General.Filter");
    map.put("/inspector/useFilter.svg", "AllIcons.General.Filter");

    map.put("/actions/showSettings.png", "AllIcons.General.GearPlain");
    map.put("/actions/showSettings.svg", "AllIcons.General.GearPlain");
    map.put("/codeStyle/Gear.png", "AllIcons.General.GearPlain");
    map.put("/codeStyle/Gear.svg", "AllIcons.General.GearPlain");
    map.put("/general/projectSettings.png", "AllIcons.General.GearPlain");
    map.put("/general/projectSettings.svg", "AllIcons.General.GearPlain");
    map.put("/general/secondaryGroup.png", "AllIcons.General.GearPlain");
    map.put("/general/secondaryGroup.svg", "AllIcons.General.GearPlain");

    map.put("/compiler/hideWarnings.png", "AllIcons.General.HideWarnings");
    map.put("/compiler/hideWarnings.svg", "AllIcons.General.HideWarnings");

    map.put("/compiler/information.png", "AllIcons.General.Information");
    map.put("/compiler/information.svg", "AllIcons.General.Information");

    map.put("/actions/consoleHistory.png", "AllIcons.General.MessageHistory");
    map.put("/actions/consoleHistory.svg", "AllIcons.General.MessageHistory");
    map.put("/vcs/messageHistory.png", "AllIcons.General.MessageHistory");
    map.put("/vcs/messageHistory.svg", "AllIcons.General.MessageHistory");

    map.put("/debugger/threadStates/locked.png", "AllIcons.Debugger.MuteBreakpoints");
    map.put("/debugger/threadStates/locked.svg", "AllIcons.Debugger.MuteBreakpoints");

    map.put("/general/autohideOff.png", "AllIcons.General.Pin_tab");
    map.put("/general/autohideOff.svg", "AllIcons.General.Pin_tab");
    map.put("/general/autohideOffInactive.png", "AllIcons.General.Pin_tab");
    map.put("/general/autohideOffInactive.svg", "AllIcons.General.Pin_tab");
    map.put("/general/autohideOffPressed.png", "AllIcons.General.Pin_tab");
    map.put("/general/autohideOffPressed.svg", "AllIcons.General.Pin_tab");

    map.put("/general/projectConfigurableBanner.png", "AllIcons.General.ProjectConfigurable");
    map.put("/general/projectConfigurableBanner.svg", "AllIcons.General.ProjectConfigurable");
    map.put("/general/projectConfigurableSelected.png", "AllIcons.General.ProjectConfigurable");
    map.put("/general/projectConfigurableSelected.svg", "AllIcons.General.ProjectConfigurable");

    map.put("/actions/exclude.png", "AllIcons.General.Remove");
    map.put("/actions/exclude.svg", "AllIcons.General.Remove");
    map.put("/toolbarDecorator/remove.png", "AllIcons.General.Remove");
    map.put("/toolbarDecorator/remove.svg", "AllIcons.General.Remove");
    map.put("/toolbarDecorator/mac/remove.png", "AllIcons.General.Remove");
    map.put("/toolbarDecorator/mac/remove.svg", "AllIcons.General.Remove");

    map.put("/general/applicationSettings.png", "AllIcons.General.Settings");
    map.put("/general/applicationSettings.svg", "AllIcons.General.Settings");
    map.put("/general/Configure.png", "AllIcons.General.Settings");
    map.put("/general/Configure.svg", "AllIcons.General.Settings");
    map.put("/general/editColors.png", "AllIcons.General.Settings");
    map.put("/general/editColors.svg", "AllIcons.General.Settings");
    map.put("/general/ideOptions.png", "AllIcons.General.Settings");
    map.put("/general/ideOptions.svg", "AllIcons.General.Settings");
    map.put("/vcs/customizeView.png", "AllIcons.General.Settings");
    map.put("/vcs/customizeView.svg", "AllIcons.General.Settings");

    map.put("/compiler/warning.png", "AllIcons.General.Warning");
    map.put("/compiler/warning.svg", "AllIcons.General.Warning");


    map.put("/hierarchy/callee.png", "AllIcons.Hierarchy.Subtypes");
    map.put("/hierarchy/callee.svg", "AllIcons.Hierarchy.Subtypes");

    map.put("/hierarchy/caller.png", "AllIcons.Hierarchy.Supertypes");
    map.put("/hierarchy/caller.svg", "AllIcons.Hierarchy.Supertypes");


    map.put("/ide/error.png", "AllIcons.Ide.FatalError");
    map.put("/ide/error.svg", "AllIcons.Ide.FatalError");


    map.put("/general/packagesTab.png", "AllIcons.Nodes.CopyOfFolder");
    map.put("/general/packagesTab.svg", "AllIcons.Nodes.CopyOfFolder");

    map.put("/nodes/newFolder.png", "AllIcons.Nodes.Folder");
    map.put("/nodes/newFolder.svg", "AllIcons.Nodes.Folder");
    map.put("/nodes/ppFile.png", "AllIcons.Nodes.Folder");
    map.put("/nodes/ppFile.svg", "AllIcons.Nodes.Folder");
    map.put("/nodes/treeClosed.png", "AllIcons.Nodes.Folder");
    map.put("/nodes/treeClosed.svg", "AllIcons.Nodes.Folder");
    map.put("/nodes/treeOpen.png", "AllIcons.Nodes.Folder");
    map.put("/nodes/treeOpen.svg", "AllIcons.Nodes.Folder");


    map.put("/toolbarDecorator/mac/addBlankLine.png", "AllIcons.ToolbarDecorator.AddBlankLine");
    map.put("/toolbarDecorator/mac/addBlankLine.svg", "AllIcons.ToolbarDecorator.AddBlankLine");

    map.put("/toolbarDecorator/mac/addClass.png", "AllIcons.ToolbarDecorator.AddClass");
    map.put("/toolbarDecorator/mac/addClass.svg", "AllIcons.ToolbarDecorator.AddClass");

    map.put("/toolbarDecorator/addPackage.png", "AllIcons.ToolbarDecorator.AddFolder");
    map.put("/toolbarDecorator/addPackage.svg", "AllIcons.ToolbarDecorator.AddFolder");
    map.put("/toolbarDecorator/mac/addFolder.png", "AllIcons.ToolbarDecorator.AddFolder");
    map.put("/toolbarDecorator/mac/addFolder.svg", "AllIcons.ToolbarDecorator.AddFolder");
    map.put("/toolbarDecorator/mac/addPackage.png", "AllIcons.ToolbarDecorator.AddFolder");
    map.put("/toolbarDecorator/mac/addPackage.svg", "AllIcons.ToolbarDecorator.AddFolder");

    map.put("/toolbarDecorator/mac/addIcon.png", "AllIcons.ToolbarDecorator.AddIcon");
    map.put("/toolbarDecorator/mac/addIcon.svg", "AllIcons.ToolbarDecorator.AddIcon");

    map.put("/toolbarDecorator/mac/addJira.png", "AllIcons.ToolbarDecorator.AddJira");
    map.put("/toolbarDecorator/mac/addJira.svg", "AllIcons.ToolbarDecorator.AddJira");

    map.put("/toolbarDecorator/mac/addLink.png", "AllIcons.ToolbarDecorator.AddLink");
    map.put("/toolbarDecorator/mac/addLink.svg", "AllIcons.ToolbarDecorator.AddLink");

    map.put("/toolbarDecorator/mac/addPattern.png", "AllIcons.ToolbarDecorator.AddPattern");
    map.put("/toolbarDecorator/mac/addPattern.svg", "AllIcons.ToolbarDecorator.AddPattern");

    map.put("/toolbarDecorator/mac/addRemoteDatasource.png", "AllIcons.ToolbarDecorator.AddRemoteDatasource");
    map.put("/toolbarDecorator/mac/addRemoteDatasource.svg", "AllIcons.ToolbarDecorator.AddRemoteDatasource");

    map.put("/toolbarDecorator/mac/addYouTrack.png", "AllIcons.ToolbarDecorator.AddYouTrack");
    map.put("/toolbarDecorator/mac/addYouTrack.svg", "AllIcons.ToolbarDecorator.AddYouTrack");

    map.put("/actions/export.png", "AllIcons.ToolbarDecorator.Export");
    map.put("/actions/export.svg", "AllIcons.ToolbarDecorator.Export");
    map.put("/graph/export.png", "AllIcons.ToolbarDecorator.Export");
    map.put("/graph/export.svg", "AllIcons.ToolbarDecorator.Export");

    map.put("/general/CreateNewProjectfromExistingFiles.png", "AllIcons.ToolbarDecorator.Import");
    map.put("/general/CreateNewProjectfromExistingFiles.svg", "AllIcons.ToolbarDecorator.Import");
    map.put("/general/importProject.png", "AllIcons.ToolbarDecorator.Import");
    map.put("/general/importProject.svg", "AllIcons.ToolbarDecorator.Import");
    map.put("/welcome/CreateNewProjectfromExistingFiles.png", "AllIcons.ToolbarDecorator.Import");
    map.put("/welcome/CreateNewProjectfromExistingFiles.svg", "AllIcons.ToolbarDecorator.Import");
    map.put("/welcome/importProject.png", "AllIcons.ToolbarDecorator.Import");
    map.put("/welcome/importProject.svg", "AllIcons.ToolbarDecorator.Import");


    map.put("/general/toolWindowChanges.png", "AllIcons.Toolwindows.ToolWindowChanges");
    map.put("/general/toolWindowChanges.svg", "AllIcons.Toolwindows.ToolWindowChanges");
    map.put("/general/toolWindowDebugger.png", "AllIcons.Toolwindows.ToolWindowDebugger");
    map.put("/general/toolWindowDebugger.svg", "AllIcons.Toolwindows.ToolWindowDebugger");


    map.put("/general/createNewProject.png", "AllIcons.Welcome.CreateNewProject");
    map.put("/general/createNewProject.svg", "AllIcons.Welcome.CreateNewProject");

    map.put("/general/getProjectfromVCS.png", "AllIcons.Welcome.FromVCS");
    map.put("/general/getProjectfromVCS.svg", "AllIcons.Welcome.FromVCS");

    deprecatedIconsReplacements = Map.copyOf(map);
  }

  @Override
  public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
    return deprecatedIconsReplacements.get(path);
  }
}
