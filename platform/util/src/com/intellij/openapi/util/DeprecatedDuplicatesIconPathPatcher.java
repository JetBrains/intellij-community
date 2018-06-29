// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class DeprecatedDuplicatesIconPathPatcher extends IconPathPatcher {
  @NonNls private static final Map<String, String> ourDeprecatedIconsReplacements = new HashMap<String, String>();

  static {
    ourDeprecatedIconsReplacements.put("/actions/prevfile.png", "AllIcons.Actions.Back");
    ourDeprecatedIconsReplacements.put("/actions/prevfile.svg", "AllIcons.Actions.Back");

    ourDeprecatedIconsReplacements.put("/actions/delete.png", "AllIcons.Actions.Cancel");
    ourDeprecatedIconsReplacements.put("/actions/delete.svg", "AllIcons.Actions.Cancel");

    ourDeprecatedIconsReplacements.put("/actions/checkedBlack.png", "AllIcons.Actions.Checked");
    ourDeprecatedIconsReplacements.put("/actions/checkedBlack.svg", "AllIcons.Actions.Checked");
    ourDeprecatedIconsReplacements.put("/actions/checkedGrey.png", "AllIcons.Actions.Checked");
    ourDeprecatedIconsReplacements.put("/actions/checkedGrey.svg", "AllIcons.Actions.Checked");

    ourDeprecatedIconsReplacements.put("/general/collapseAll.png", "AllIcons.Actions.Collapseall");
    ourDeprecatedIconsReplacements.put("/general/collapseAll.svg", "AllIcons.Actions.Collapseall");
    ourDeprecatedIconsReplacements.put("/general/collapseAllHover.png", "AllIcons.Actions.Collapseall");
    ourDeprecatedIconsReplacements.put("/general/collapseAllHover.svg", "AllIcons.Actions.Collapseall");

    ourDeprecatedIconsReplacements.put("/actions/get.png", "AllIcons.Actions.Download");
    ourDeprecatedIconsReplacements.put("/actions/get.svg", "AllIcons.Actions.Download");

    ourDeprecatedIconsReplacements.put("/modules/edit.png", "AllIcons.Actions.Edit");
    ourDeprecatedIconsReplacements.put("/modules/edit.svg", "AllIcons.Actions.Edit");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/edit.png", "AllIcons.Actions.Edit");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/edit.svg", "AllIcons.Actions.Edit");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/edit.png", "AllIcons.Actions.Edit");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/edit.svg", "AllIcons.Actions.Edit");

    ourDeprecatedIconsReplacements.put("/general/expandAll.png", "AllIcons.Actions.Expandall");
    ourDeprecatedIconsReplacements.put("/general/expandAll.svg", "AllIcons.Actions.Expandall");
    ourDeprecatedIconsReplacements.put("/general/expandAllHover.png", "AllIcons.Actions.Expandall");
    ourDeprecatedIconsReplacements.put("/general/expandAllHover.svg", "AllIcons.Actions.Expandall");

    ourDeprecatedIconsReplacements.put("/actions/findPlain.png", "AllIcons.Actions.Find");
    ourDeprecatedIconsReplacements.put("/actions/findPlain.svg", "AllIcons.Actions.Find");
    ourDeprecatedIconsReplacements.put("/actions/menu_find.png", "AllIcons.Actions.Find");
    ourDeprecatedIconsReplacements.put("/actions/menu_find.svg", "AllIcons.Actions.Find");

    ourDeprecatedIconsReplacements.put("/actions/nextfile.png", "AllIcons.Actions.Forward");
    ourDeprecatedIconsReplacements.put("/actions/nextfile.svg", "AllIcons.Actions.Forward");
    ourDeprecatedIconsReplacements.put("/hierarchy/base.png", "AllIcons.Actions.Forward");
    ourDeprecatedIconsReplacements.put("/hierarchy/base.svg", "AllIcons.Actions.Forward");

    ourDeprecatedIconsReplacements.put("/objectBrowser/showModules.png", "AllIcons.Actions.GroupByModule");
    ourDeprecatedIconsReplacements.put("/objectBrowser/showModules.svg", "AllIcons.Actions.GroupByModule");

    ourDeprecatedIconsReplacements.put("/toolbar/folders.png", "AllIcons.Actions.GroupByPackage");
    ourDeprecatedIconsReplacements.put("/toolbar/folders.svg", "AllIcons.Actions.GroupByPackage");

    ourDeprecatedIconsReplacements.put("/actions/menu_help.png", "AllIcons.Actions.Help");
    ourDeprecatedIconsReplacements.put("/actions/menu_help.svg", "AllIcons.Actions.Help");
    ourDeprecatedIconsReplacements.put("/debugger/readHelp.png", "AllIcons.Actions.Help");
    ourDeprecatedIconsReplacements.put("/debugger/readHelp.svg", "AllIcons.Actions.Help");
    ourDeprecatedIconsReplacements.put("/runConfigurations/unknown.png", "AllIcons.Actions.Help");
    ourDeprecatedIconsReplacements.put("/runConfigurations/unknown.svg", "AllIcons.Actions.Help");

    ourDeprecatedIconsReplacements.put("/actions/createFromUsage.png", "AllIcons.Actions.IntentionBulb");
    ourDeprecatedIconsReplacements.put("/actions/createFromUsage.svg", "AllIcons.Actions.IntentionBulb");

    ourDeprecatedIconsReplacements.put("/runConfigurations/variables.png", "AllIcons.Actions.ListFiles");
    ourDeprecatedIconsReplacements.put("/runConfigurations/variables.svg", "AllIcons.Actions.ListFiles");

    ourDeprecatedIconsReplacements.put("/general/openProject.png", "AllIcons.Actions.Menu_open");
    ourDeprecatedIconsReplacements.put("/general/openProject.svg", "AllIcons.Actions.Menu_open");
    ourDeprecatedIconsReplacements.put("/welcome/openProject.png", "AllIcons.Actions.Menu_open");
    ourDeprecatedIconsReplacements.put("/welcome/openProject.svg", "AllIcons.Actions.Menu_open");

    ourDeprecatedIconsReplacements.put("/debugger/threadStates/io.png", "AllIcons.Actions.Menu_saveall");
    ourDeprecatedIconsReplacements.put("/debugger/threadStates/io.svg", "AllIcons.Actions.Menu_saveall");
    ourDeprecatedIconsReplacements.put("/runConfigurations/saveTempConfig.png", "AllIcons.Actions.Menu_saveall");
    ourDeprecatedIconsReplacements.put("/runConfigurations/saveTempConfig.svg", "AllIcons.Actions.Menu_saveall");


    ourDeprecatedIconsReplacements.put("/actions/sortDesc.png", "AllIcons.Actions.MoveDown");
    ourDeprecatedIconsReplacements.put("/actions/sortDesc.svg", "AllIcons.Actions.MoveDown");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/moveDown.png", "AllIcons.Actions.MoveDown");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/moveDown.svg", "AllIcons.Actions.MoveDown");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/moveDown.png", "AllIcons.Actions.MoveDown");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/moveDown.svg", "AllIcons.Actions.MoveDown");

    ourDeprecatedIconsReplacements.put("/actions/moveToStandardPlace.png", "AllIcons.Actions.MoveTo2");
    ourDeprecatedIconsReplacements.put("/actions/moveToStandardPlace.svg", "AllIcons.Actions.MoveTo2");

    ourDeprecatedIconsReplacements.put("/actions/sortAsc.png", "AllIcons.Actions.MoveUp");
    ourDeprecatedIconsReplacements.put("/actions/sortAsc.svg", "AllIcons.Actions.MoveUp");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/moveUp.png", "AllIcons.Actions.MoveUp");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/moveUp.svg", "AllIcons.Actions.MoveUp");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/moveUp.png", "AllIcons.Actions.MoveUp");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/moveUp.svg", "AllIcons.Actions.MoveUp");

    ourDeprecatedIconsReplacements.put("/debugger/stackFrame.png", "AllIcons.Debugger.Frame");
    ourDeprecatedIconsReplacements.put("/debugger/stackFrame.svg", "AllIcons.Debugger.Frame");

    ourDeprecatedIconsReplacements.put("/debugger/threadStates/paused.png", "AllIcons.Actions.Pause");
    ourDeprecatedIconsReplacements.put("/debugger/threadStates/paused.svg", "AllIcons.Actions.Pause");

    ourDeprecatedIconsReplacements.put("/actions/showSource.png", "AllIcons.Actions.Preview");
    ourDeprecatedIconsReplacements.put("/actions/showSource.svg", "AllIcons.Actions.Preview");

    ourDeprecatedIconsReplacements.put("/actions/browser-externalJavaDoc.png", "AllIcons.Actions.PreviousOccurence");
    ourDeprecatedIconsReplacements.put("/actions/browser-externalJavaDoc.svg", "AllIcons.Actions.PreviousOccurence");

    ourDeprecatedIconsReplacements.put("/actions/sync.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/sync.svg", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/synchronizeFS.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/synchronizeFS.svg", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/vcs/refresh.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/vcs/refresh.svg", "AllIcons.Actions.Refresh");

    ourDeprecatedIconsReplacements.put("/actions/menu_replace.png", "AllIcons.Actions.Replace");
    ourDeprecatedIconsReplacements.put("/actions/menu_replace.svg", "AllIcons.Actions.Replace");

    ourDeprecatedIconsReplacements.put("/actions/refreshUsages.png", "AllIcons.Actions.Rerun");
    ourDeprecatedIconsReplacements.put("/actions/refreshUsages.svg", "AllIcons.Actions.Rerun");

    ourDeprecatedIconsReplacements.put("/debugger/threadStates/running.png", "AllIcons.Actions.Resume");
    ourDeprecatedIconsReplacements.put("/debugger/threadStates/running.svg", "AllIcons.Actions.Resume");

    ourDeprecatedIconsReplacements.put("/actions/reset.png", "AllIcons.Actions.Rollback");
    ourDeprecatedIconsReplacements.put("/actions/reset.svg", "AllIcons.Actions.Rollback");

    ourDeprecatedIconsReplacements.put("/general/recursive.png", "AllIcons.Actions.ShowAsTree");
    ourDeprecatedIconsReplacements.put("/general/recursive.svg", "AllIcons.Actions.ShowAsTree");
    ourDeprecatedIconsReplacements.put("/vcs/mergeSourcesTree.png", "AllIcons.Actions.ShowAsTree");
    ourDeprecatedIconsReplacements.put("/vcs/mergeSourcesTree.svg", "AllIcons.Actions.ShowAsTree");

    ourDeprecatedIconsReplacements.put("/actions/submit1.png", "AllIcons.Actions.SetDefault");
    ourDeprecatedIconsReplacements.put("/actions/submit1.svg", "AllIcons.Actions.SetDefault");

    ourDeprecatedIconsReplacements.put("/general/debug.png", "AllIcons.Actions.StartDebugger");
    ourDeprecatedIconsReplacements.put("/general/debug.svg", "AllIcons.Actions.StartDebugger");


    ourDeprecatedIconsReplacements.put("/codeStyle/mac/AddNewSectionRule.png", "AllIcons.CodeStyle.AddNewSectionRule");
    ourDeprecatedIconsReplacements.put("/codeStyle/mac/AddNewSectionRule.svg", "AllIcons.CodeStyle.AddNewSectionRule");


    ourDeprecatedIconsReplacements.put("/debugger/watches.png", "AllIcons.Debugger.Watch");
    ourDeprecatedIconsReplacements.put("/debugger/watches.svg", "AllIcons.Debugger.Watch");


    ourDeprecatedIconsReplacements.put("/debugger/newWatch.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/debugger/newWatch.svg", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/modules/addContentEntry.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/modules/addContentEntry.svg", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/add.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/add.svg", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/add.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/add.svg", "AllIcons.General.Add");

    ourDeprecatedIconsReplacements.put("/runConfigurations/configurationWarning.png", "AllIcons.General.BalloonError");
    ourDeprecatedIconsReplacements.put("/runConfigurations/configurationWarning.svg", "AllIcons.General.BalloonError");

    ourDeprecatedIconsReplacements.put("/compiler/error.png", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/compiler/error.svg", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/ide/errorSign.png", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/ide/errorSign.svg", "AllIcons.General.Error");

    ourDeprecatedIconsReplacements.put("/general/externalTools.png", "AllIcons.General.ExternalToolsSmall");
    ourDeprecatedIconsReplacements.put("/general/externalTools.svg", "AllIcons.General.ExternalToolsSmall");

    ourDeprecatedIconsReplacements.put("/actions/filter_small.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/actions/filter_small.svg", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/ant/filter.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/ant/filter.svg", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/debugger/class_filter.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/debugger/class_filter.svg", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/inspector/useFilter.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/inspector/useFilter.svg", "AllIcons.General.Filter");

    ourDeprecatedIconsReplacements.put("/actions/showSettings.png", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/actions/showSettings.svg", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/codeStyle/Gear.png", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/codeStyle/Gear.svg", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/general/projectSettings.png", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/general/projectSettings.svg", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/general/secondaryGroup.png", "AllIcons.General.GearPlain");
    ourDeprecatedIconsReplacements.put("/general/secondaryGroup.svg", "AllIcons.General.GearPlain");

    ourDeprecatedIconsReplacements.put("/compiler/hideWarnings.png", "AllIcons.General.HideWarnings");
    ourDeprecatedIconsReplacements.put("/compiler/hideWarnings.svg", "AllIcons.General.HideWarnings");

    ourDeprecatedIconsReplacements.put("/compiler/information.png", "AllIcons.General.Information");
    ourDeprecatedIconsReplacements.put("/compiler/information.svg", "AllIcons.General.Information");

    ourDeprecatedIconsReplacements.put("/actions/consoleHistory.png", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/actions/consoleHistory.svg", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/vcs/messageHistory.png", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/vcs/messageHistory.svg", "AllIcons.General.MessageHistory");

    ourDeprecatedIconsReplacements.put("/debugger/threadStates/locked.png", "AllIcons.Debugger.MuteBreakpoints");
    ourDeprecatedIconsReplacements.put("/debugger/threadStates/locked.svg", "AllIcons.Debugger.MuteBreakpoints");

    ourDeprecatedIconsReplacements.put("/general/autohideOff.png", "AllIcons.General.Pin_tab");
    ourDeprecatedIconsReplacements.put("/general/autohideOff.svg", "AllIcons.General.Pin_tab");
    ourDeprecatedIconsReplacements.put("/general/autohideOffInactive.png", "AllIcons.General.Pin_tab");
    ourDeprecatedIconsReplacements.put("/general/autohideOffInactive.svg", "AllIcons.General.Pin_tab");
    ourDeprecatedIconsReplacements.put("/general/autohideOffPressed.png", "AllIcons.General.Pin_tab");
    ourDeprecatedIconsReplacements.put("/general/autohideOffPressed.svg", "AllIcons.General.Pin_tab");

    ourDeprecatedIconsReplacements.put("/general/projectConfigurableBanner.png", "AllIcons.General.ProjectConfigurable");
    ourDeprecatedIconsReplacements.put("/general/projectConfigurableBanner.svg", "AllIcons.General.ProjectConfigurable");
    ourDeprecatedIconsReplacements.put("/general/projectConfigurableSelected.png", "AllIcons.General.ProjectConfigurable");
    ourDeprecatedIconsReplacements.put("/general/projectConfigurableSelected.svg", "AllIcons.General.ProjectConfigurable");

    ourDeprecatedIconsReplacements.put("/actions/exclude.png", "AllIcons.General.Remove");
    ourDeprecatedIconsReplacements.put("/actions/exclude.svg", "AllIcons.General.Remove");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/remove.png", "AllIcons.General.Remove");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/remove.svg", "AllIcons.General.Remove");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/remove.png", "AllIcons.General.Remove");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/remove.svg", "AllIcons.General.Remove");

    ourDeprecatedIconsReplacements.put("/general/applicationSettings.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/applicationSettings.svg", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/Configure.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/Configure.svg", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/editColors.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/editColors.svg", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/ideOptions.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/ideOptions.svg", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/vcs/customizeView.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/vcs/customizeView.svg", "AllIcons.General.Settings");

    ourDeprecatedIconsReplacements.put("/compiler/warning.png", "AllIcons.General.Warning");
    ourDeprecatedIconsReplacements.put("/compiler/warning.svg", "AllIcons.General.Warning");


    ourDeprecatedIconsReplacements.put("/hierarchy/callee.png", "AllIcons.Hierarchy.Subtypes");
    ourDeprecatedIconsReplacements.put("/hierarchy/callee.svg", "AllIcons.Hierarchy.Subtypes");

    ourDeprecatedIconsReplacements.put("/hierarchy/caller.png", "AllIcons.Hierarchy.Supertypes");
    ourDeprecatedIconsReplacements.put("/hierarchy/caller.svg", "AllIcons.Hierarchy.Supertypes");


    ourDeprecatedIconsReplacements.put("/ide/error.png", "AllIcons.Ide.FatalError");
    ourDeprecatedIconsReplacements.put("/ide/error.svg", "AllIcons.Ide.FatalError");

    ourDeprecatedIconsReplacements.put("/ide/notification/closeHover.png", "AllIcons.Ide.Notification.Close");
    ourDeprecatedIconsReplacements.put("/ide/notification/closeHover.svg", "AllIcons.Ide.Notification.Close");


    ourDeprecatedIconsReplacements.put("/general/packagesTab.png", "AllIcons.Nodes.CopyOfFolder");
    ourDeprecatedIconsReplacements.put("/general/packagesTab.svg", "AllIcons.Nodes.CopyOfFolder");

    ourDeprecatedIconsReplacements.put("/nodes/newFolder.png", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/newFolder.svg", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/ppFile.png", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/ppFile.svg", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/treeClosed.png", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/treeClosed.svg", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/treeOpen.png", "AllIcons.Nodes.Folder");
    ourDeprecatedIconsReplacements.put("/nodes/treeOpen.svg", "AllIcons.Nodes.Folder");


    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addBlankLine.png", "AllIcons.ToolbarDecorator.AddBlankLine");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addBlankLine.svg", "AllIcons.ToolbarDecorator.AddBlankLine");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addClass.png", "AllIcons.ToolbarDecorator.AddClass");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addClass.svg", "AllIcons.ToolbarDecorator.AddClass");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/addPackage.png", "AllIcons.ToolbarDecorator.AddFolder");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/addPackage.svg", "AllIcons.ToolbarDecorator.AddFolder");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addFolder.png", "AllIcons.ToolbarDecorator.AddFolder");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addFolder.svg", "AllIcons.ToolbarDecorator.AddFolder");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addPackage.png", "AllIcons.ToolbarDecorator.AddFolder");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addPackage.svg", "AllIcons.ToolbarDecorator.AddFolder");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addIcon.png", "AllIcons.ToolbarDecorator.AddIcon");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addIcon.svg", "AllIcons.ToolbarDecorator.AddIcon");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addJira.png", "AllIcons.ToolbarDecorator.AddJira");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addJira.svg", "AllIcons.ToolbarDecorator.AddJira");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addLink.png", "AllIcons.ToolbarDecorator.AddLink");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addLink.svg", "AllIcons.ToolbarDecorator.AddLink");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addPattern.png", "AllIcons.ToolbarDecorator.AddPattern");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addPattern.svg", "AllIcons.ToolbarDecorator.AddPattern");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addRemoteDatasource.png", "AllIcons.ToolbarDecorator.AddRemoteDatasource");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addRemoteDatasource.svg", "AllIcons.ToolbarDecorator.AddRemoteDatasource");

    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addYouTrack.png", "AllIcons.ToolbarDecorator.AddYouTrack");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/mac/addYouTrack.svg", "AllIcons.ToolbarDecorator.AddYouTrack");

    ourDeprecatedIconsReplacements.put("/actions/export.png", "AllIcons.ToolbarDecorator.Export");
    ourDeprecatedIconsReplacements.put("/actions/export.svg", "AllIcons.ToolbarDecorator.Export");
    ourDeprecatedIconsReplacements.put("/graph/export.png", "AllIcons.ToolbarDecorator.Export");
    ourDeprecatedIconsReplacements.put("/graph/export.svg", "AllIcons.ToolbarDecorator.Export");

    ourDeprecatedIconsReplacements.put("/general/CreateNewProjectfromExistingFiles.png", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/general/CreateNewProjectfromExistingFiles.svg", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/general/importProject.png", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/general/importProject.svg", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/welcome/CreateNewProjectfromExistingFiles.png", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/welcome/CreateNewProjectfromExistingFiles.svg", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/welcome/importProject.png", "AllIcons.ToolbarDecorator.Import");
    ourDeprecatedIconsReplacements.put("/welcome/importProject.svg", "AllIcons.ToolbarDecorator.Import");


    ourDeprecatedIconsReplacements.put("/general/toolWindowChanges.png", "AllIcons.Toolwindows.ToolWindowChanges");
    ourDeprecatedIconsReplacements.put("/general/toolWindowChanges.svg", "AllIcons.Toolwindows.ToolWindowChanges");
    ourDeprecatedIconsReplacements.put("/general/toolWindowDebugger.png", "AllIcons.Toolwindows.ToolWindowDebugger");
    ourDeprecatedIconsReplacements.put("/general/toolWindowDebugger.svg", "AllIcons.Toolwindows.ToolWindowDebugger");


    ourDeprecatedIconsReplacements.put("/general/createNewProject.png", "AllIcons.Welcome.CreateNewProject");
    ourDeprecatedIconsReplacements.put("/general/createNewProject.svg", "AllIcons.Welcome.CreateNewProject");

    ourDeprecatedIconsReplacements.put("/general/getProjectfromVCS.png", "AllIcons.Welcome.FromVCS");
    ourDeprecatedIconsReplacements.put("/general/getProjectfromVCS.svg", "AllIcons.Welcome.FromVCS");
  }

  @Nullable
  @Override
  public String patchPath(String path, ClassLoader classLoader) {
    return ourDeprecatedIconsReplacements.get(path);
  }
}
