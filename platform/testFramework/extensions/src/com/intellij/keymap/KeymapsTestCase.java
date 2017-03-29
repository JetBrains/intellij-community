/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.keymap;

import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class KeymapsTestCase extends KeymapsTestCaseBase {
  @Override
  protected void collectKnownDuplicates(Map<String, Map<String, List<String>>> result) {
    appendKnownDuplicates(result, DEFAULT_DUPLICATES);
  }

  @Override
  protected void collectUnknownActions(Set<String> result) {
    result.addAll(UNCNOWN_ACTION_IDS);
  }

  @Override
  protected Set<String> getBoundActions() {
    return DEFAULT_BOUND_ACTIONS;
  }

  // @formatter:off
  @NonNls @SuppressWarnings({"HardCodedStringLiteral"})
  private static final Map<String, String[][]> DEFAULT_DUPLICATES = new THashMap<String, String[][]>(){{
    put("$default", new String[][] {
    { "ADD",                      "ExpandTreeNode", "Graph.ZoomIn"},
    { "BACK_SPACE",               "EditorBackSpace", "Images.Thumbnails.UpFolder"},
    { "ENTER",                    "EditorChooseLookupItem", "NextTemplateVariable", "EditorEnter", "Images.Thumbnails.EnterAction",
                                  "PropertyInspectorActions.EditValue", "Console.Execute", "Console.TableResult.EditValue"},
    { "F2",                       "GotoNextError", "GuiDesigner.EditComponent", "GuiDesigner.EditGroup", "Console.TableResult.EditValue", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
    { "alt ENTER",                "ShowIntentionActions", "Console.TableResult.EditValue", "DatabaseView.PropertiesAction"},
    { "F5",                       "UML.ApplyCurrentLayout", "CopyElement"},
    { "F7",                       "NextDiff", "StepInto"},
    { "INSERT",                   "EditorToggleInsertState", "UsageView.Include", "DomElementsTreeView.AddElement", "DomCollectionControl.Add", "XDebugger.NewWatch"},
    { "SUBTRACT",                 "CollapseTreeNode", "Graph.ZoomOut"},
    { "TAB",                      "EditorChooseLookupItemReplace", "NextTemplateVariable", "NextParameter", "EditorIndentSelection", "EditorTab", "NextTemplateParameter", "ExpandLiveTemplateByTab"},
    { "alt DOWN",                 "ShowContent", "MethodDown", "Arrangement.Rule.Match.Condition.Move.Down"},
    { "alt UP",                   "MethodUp", "Arrangement.Rule.Match.Condition.Move.Up"},
    { "alt F1",                   "SelectIn", "ProjectViewChangeView"},
    { "alt INSERT",               "FileChooser.NewFolder", "Generate", "NewElement"},
    { "control F10",              "javaee.UpdateRunningApplication", "liveedit.UpdateRunningApplication"},
    { "control 1",                "FileChooser.GotoHome", "GotoBookmark1", "DuplicatesForm.SendToLeft"},
    { "control 2",                "FileChooser.GotoProject", "GotoBookmark2", "DuplicatesForm.SendToRight"},
    { "control 3",                "GotoBookmark3", "FileChooser.GotoModule"},
    { "control ADD",              "ExpandAll", "ExpandRegion"},
    { "control DIVIDE",           "CommentByLineComment", "Images.Editor.ActualSize"},
    { "control DOWN",             "EditorScrollDown", "EditorLookupDown", "MethodOverloadSwitchDown"},
    { "control ENTER",            "EditorSplitLine", "ViewSource", "Console.Execute.Multiline"},
    { "control EQUALS",           "ExpandAll", "ExpandRegion"},
    { "control F5",               "Refresh", "Rerun"},
    { "control D",                "EditorDuplicate", "Diff.ShowDiff", "CompareTwoFiles", "SendEOF", "FileChooser.GotoDesktop"},
    { "control L",                "FindNext", "Vcs.Log.FocusTextFilter"},
    { "control M",                "EditorScrollToCenter", "Vcs.ShowMessageHistory"},
    { "control N",                "FileChooser.NewFolder", "GotoClass", "GotoChangedFile"},
    { "control P",                "FileChooser.TogglePathShowing", "ParameterInfo"},
    { "control R",                "Replace", "Console.TableResult.Reload", "org.jetbrains.plugins.ruby.rails.console.ReloadSources"},
    { "control SLASH",            "CommentByLineComment", "Images.Editor.ActualSize"},
    { "control U",                "GotoSuperMethod", "CommanderSwapPanels"},
    { "control UP",               "EditorScrollUp", "EditorLookupUp", "MethodOverloadSwitchUp"},
    { "control alt A",            "ChangesView.AddUnversioned", "Diagram.DeselectAll"},
    { "control alt E",            "PerforceDirect.Edit", "Console.History.Browse"},
    { "control alt DOWN",         "NextOccurence", "Console.TableResult.NextPage"},
    { "control alt G",            "org.jetbrains.plugins.ruby.rails.actions.generators.GeneratorsPopupAction", "Mvc.RunTarget"},
    { "control alt R",            "org.jetbrains.plugins.ruby.tasks.rake.actions.RakeTasksPopupAction", "Django.RunManageTaskAction"},
    { "control alt UP",           "PreviousOccurence", "Console.TableResult.PreviousPage"},
    { "control alt N",            "Inline", "Console.TableResult.SetNull"},
    { "ctrl alt H",               "CallHierarchy", "ChangesView.ShelveSilently"},
    { "ctrl alt U",               "ShowUmlDiagramPopup", "ChangesView.UnshelveSilently"}, 
    { "control MINUS",            "CollapseAll", "CollapseRegion"},
    { "control PERIOD",           "EditorChooseLookupItemDot", "CollapseSelection"},
    { "shift DELETE",             "$Cut", "Maven.Uml.Exclude"},
    { "shift ENTER",              "EditorStartNewLine", "Console.TableResult.EditValueMaximized", "OpenElementInNewWindow"},
    { "shift F4",                 "Debugger.EditTypeSource", "EditSourceInNewWindow"},
    { "shift F7",                 "PreviousDiff", "SmartStepInto"},
    { "shift TAB",                "PreviousTemplateVariable", "PrevParameter", "EditorUnindentSelection", "PrevTemplateParameter"},
    { "shift alt L",              "org.jetbrains.plugins.ruby.console.LoadInIrbConsoleAction", "context.load"},
    { "shift control D",          "TagDocumentationNavigation", "Diff.ShowSettingsPopup", "Uml.ShowDiff"},
    { "shift control DOWN",       "ResizeToolWindowDown", "MoveStatementDown"},
    { "shift control ENTER",      "EditorCompleteStatement", "Console.Jpa.GenerateSql"},
    { "shift control F10",        "Console.Open", "RunClass", "RunTargetAction"},
    { "shift control F8",         "ViewBreakpoints", "EditBreakpoint"},
    { "shift control G",          "ClassTemplateNavigation", "GoToClass"},
    { "shift control LEFT",       "EditorPreviousWordWithSelection", "ResizeToolWindowLeft", },
    { "shift control RIGHT",      "EditorNextWordWithSelection", "ResizeToolWindowRight", },
    { "shift control T",          "GotoTest", "Images.ShowThumbnails"},
    { "shift control UP",         "ResizeToolWindowUp", "MoveStatementUp"},
    { "shift control alt DOWN",   "VcsShowNextChangeMarker", "HtmlTableCellNavigateDown"},
    { "shift control alt UP",     "VcsShowPrevChangeMarker", "HtmlTableCellNavigateUp"},
    { "shift control alt D",      "UML.ShowChanges", "Console.TableResult.CloneColumn"},
    { "shift control U",          "ShelveChanges.UnshelveWithDialog", "EditorToggleCase"},
    { "control E",                "RecentFiles", "Vcs.ShowMessageHistory"},
    { "control alt Z",            "Vcs.RollbackChangedLines", "ChangesView.Revert"},
    { "control TAB",              "Switcher", "Diff.FocusOppositePane"},
    { "shift control TAB",        "Switcher", "Diff.FocusOppositePaneAndScroll"},
    { "control alt I",            "DatabaseView.GenerateScriptIntoConsole", "AutoIndentLines"},
    { "ctrl alt ENTER",           "EditorStartNewLineBefore", "QuickActionPopup"},
    });
    put("Mac OS X 10.5+", new String[][] {
    { "F5",                       "CopyElement", "Console.TableResult.Reload", "UML.ApplyCurrentLayout"},
    { "BACK_SPACE",               "$Delete", "EditorBackSpace", "Images.Thumbnails.UpFolder"},
    { "shift BACK_SPACE",         "EditorBackSpace", "UsageView.Include"},
    { "meta BACK_SPACE",          "EditorDeleteLine", "$Delete"},
    { "control DOWN",             "ShowContent", "EditorLookupDown", "MethodDown"},
    { "control UP",               "EditorLookupUp", "MethodUp"},
    { "control TAB",              "Switcher", "Diff.FocusOppositePane"},
    { "shift control TAB",        "Switcher", "Diff.FocusOppositePaneAndScroll"},
    { "meta L",                   "Vcs.Log.FocusTextFilter", "GotoLine"},
    { "meta R",                   "Refresh", "Rerun", "Replace", "org.jetbrains.plugins.ruby.rails.console.ReloadSources"},
    { "control O",                "ExportToTextFile", "OverrideMethods", },
    { "control ENTER",            "Generate", "NewElement"},
    { "meta 1",                   "ActivateProjectToolWindow", "FileChooser.GotoHome", "DuplicatesForm.SendToLeft"},
    { "meta 2",                   "ActivateFavoritesToolWindow", "FileChooser.GotoProject", "DuplicatesForm.SendToRight"},
    { "meta 3",                   "ActivateFindToolWindow", "FileChooser.GotoModule"},
    { "meta N",                   "FileChooser.NewFolder", "Generate", "NewElement"},
    { "meta O",                   "GotoClass", "GotoChangedFile"},
    { "meta UP",                  "ShowNavBar", "MethodOverloadSwitchUp"},
    { "meta DOWN",                "EditSource", "MethodOverloadSwitchDown"},
    { "shift meta G",             "ClassTemplateNavigation", "GoToClass", "FindPrevious"},
    { "shift meta LEFT",          "EditorLineStartWithSelection", "ResizeToolWindowLeft", },
    { "shift meta RIGHT",         "EditorLineEndWithSelection", "ResizeToolWindowRight", },
    { "meta E",                   "RecentFiles", "Vcs.ShowMessageHistory"},
    { "alt R",                    "Django.RunManageTaskAction", "org.jetbrains.plugins.ruby.tasks.rake.actions.RakeTasksPopupAction"},
    { "alt DOWN",                 "EditorUnSelectWord", "Arrangement.Rule.Match.Condition.Move.Down"},
    { "alt UP",                   "EditorSelectWord", "Arrangement.Rule.Match.Condition.Move.Up"},
    { "ctrl m",                   "EditorMatchBrace", "Vcs.ShowMessageHistory"},
    });
    put("Mac OS X", new String[][] {
    { "BACK_SPACE",               "$Delete", "EditorBackSpace", "Images.Thumbnails.UpFolder"},
    { "control DOWN",             "EditorLookupDown", "ShowContent", "MethodDown"},
    { "control UP",               "EditorLookupUp", "MethodUp"},
    { "control ENTER",            "Generate", "NewElement"},
    { "control F5",               "Refresh", "Rerun"},
    { "control TAB",              "Switcher", "Diff.FocusOppositePane"},
    { "shift control TAB",        "Switcher", "Diff.FocusOppositePaneAndScroll"},
    { "meta 1",                   "ActivateProjectToolWindow", "FileChooser.GotoHome", "DuplicatesForm.SendToLeft"},
    { "meta 2",                   "ActivateFavoritesToolWindow", "FileChooser.GotoProject", "DuplicatesForm.SendToRight"},
    { "meta 3",                   "ActivateFindToolWindow", "FileChooser.GotoModule"},
    { "meta E",                   "RecentFiles", "Vcs.ShowMessageHistory"},
    { "shift meta LEFT",          "EditorLineStartWithSelection", "ResizeToolWindowLeft", },
    { "shift meta RIGHT",         "EditorLineEndWithSelection", "ResizeToolWindowRight", },
    { "alt R",                    "Django.RunManageTaskAction", "org.jetbrains.plugins.ruby.tasks.rake.actions.RakeTasksPopupAction"},
    });
    put("Emacs", new String[][] {
    { "ENTER",                    "EditorChooseLookupItem", "NextTemplateVariable", "EditorEnter", "Images.Thumbnails.EnterAction",
                                  "PropertyInspectorActions.EditValue", "Console.Execute", "Console.TableResult.EditValue"},
    { "F2",                       "GotoNextError", "GuiDesigner.EditComponent", "GuiDesigner.EditGroup", "Console.TableResult.EditValue", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
    { "alt ENTER",                "ShowIntentionActions", "Console.TableResult.EditValue", "DatabaseView.PropertiesAction"},
    { "TAB",                      "EditorChooseLookupItemReplace", "NextTemplateVariable", "NextParameter", "EditorIndentSelection", 
                                  "EmacsStyleIndent", "NextTemplateParameter", "ExpandLiveTemplateByTab"},
    { "alt DOWN",                 "ShowContent", "MethodDown", "Arrangement.Rule.Match.Condition.Move.Down"},
    { "alt UP",                   "MethodUp", "Arrangement.Rule.Match.Condition.Move.Up"},
    { "alt SLASH",                "CodeCompletion", "HippieCompletion"},
    { "control 1",                "FileChooser.GotoHome", "GotoBookmark1", "DuplicatesForm.SendToLeft"},
    { "control 2",                "FileChooser.GotoProject", "GotoBookmark2", "DuplicatesForm.SendToRight"},
    { "control 3",                "GotoBookmark3", "FileChooser.GotoModule"},
    { "control D",                "$Delete", "Diff.ShowDiff", "CompareTwoFiles", "SendEOF", "FileChooser.GotoDesktop"},
    { "control K",                "EditorCutLineEnd", "CheckinProject"},
    { "control L",                "EditorScrollToCenter", "Vcs.Log.FocusTextFilter"},
    { "control M",                "EditorEnter", "EditorChooseLookupItem", "NextTemplateVariable", "Console.Execute"},
    { "control N",                "EditorDown", "FileChooser.NewFolder"},
    { "control P",                "EditorUp", "FileChooser.TogglePathShowing"},
    { "control R",                "Console.TableResult.Reload", "org.jetbrains.plugins.ruby.rails.console.ReloadSources", "FindPrevious"},
    { "control SLASH",            "$Undo", "Images.Editor.ActualSize"},
    { "control X",                "GotoFile", "SaveAll", "NextTab", "PreviousTab", "CloseContent", "CloseAllEditors", "NextSplitter",
                                  "GotoNextError", "NextProjectWindow", "EditorSwapSelectionBoundaries", "SplitVertically",
                                  "SplitHorizontally", "UnsplitAll", "Switcher", "$SelectAll"},
    { "control UP",               "EditorBackwardParagraph", "EditorLookupUp", "MethodOverloadSwitchUp"},
    { "control DOWN",             "EditorForwardParagraph", "EditorLookupDown", "MethodOverloadSwitchDown"},
    { "control alt A",            "MethodUp", "ChangesView.AddUnversioned", "Diagram.DeselectAll"},
    { "control alt E",            "MethodDown", "PerforceDirect.Edit", "Console.History.Browse"},
    { "control alt G",            "GotoDeclaration", "org.jetbrains.plugins.ruby.rails.actions.generators.GeneratorsPopupAction", "Mvc.RunTarget"},
    { "control alt S",            "ShowSettings", "Find"},
    { "shift DELETE",             "$Cut", "Maven.Uml.Exclude"},
    { "shift alt S",              "FindUsages", "context.save"},
    { "shift alt G",              "GotoChangedFile", "GotoClass", "hg4idea.QGotoFromPatches"},
    { "shift alt P",              "ParameterInfo", "hg4idea.QPushAction"},
    { "control alt I",            "DatabaseView.GenerateScriptIntoConsole", "AutoIndentLines"},
    { "shift control X",          "GotoPreviousError", "com.jetbrains.php.framework.FrameworkRunConsoleAction"},
    });
    put("Visual Studio", new String[][] {
    { "F5",                       "Resume", "UML.ApplyCurrentLayout"},
    { "F7",                       "NextDiff", "CompileDirty"},
    { "alt F2",                   "ShowBookmarks", "WebOpenInAction"},
    { "alt F8",                   "ReformatCode", "ForceStepInto", "EvaluateExpression"},
    { "alt INSERT",               "FileChooser.NewFolder", "Generate", "NewElement"},
    { "control DIVIDE",           "CommentByLineComment", "Images.Editor.ActualSize"},
    { "control COMMA",            "GotoClass", "GotoChangedFile"},
    { "control F1",               "ExternalJavaDoc", "ShowErrorDescription"},
    { "control F10",              "RunToCursor", "javaee.UpdateRunningApplication", "liveedit.UpdateRunningApplication"},
    { "control F5",               "Rerun", "Run"},
    { "control N",                "FileChooser.NewFolder", "Generate", },
    { "control P",                "FileChooser.TogglePathShowing", "Print"},
    { "control SLASH",            "CommentByLineComment", "Images.Editor.ActualSize"},
    { "control alt F",            "ReformatCode", "IntroduceField"},
    { "shift F1",                 "QuickJavaDoc", "ExternalJavaDoc"},
    { "shift F12",                "RestoreDefaultLayout", "FindUsagesInFile"},
    { "shift F2",                 "GotoPreviousError", "GotoDeclaration"},
    { "shift control F7",         "FindUsagesInFile", "HighlightUsagesInFile"},
    { "shift control I",          "ImplementMethods", "QuickImplementations"},
    { "alt F9",                   "ViewBreakpoints", "EditBreakpoint"},
    { "alt MULTIPLY",             "ShowExecutionPoint", "Images.Thumbnails.ToggleRecursive"},
    });
    put("Default for XWin", new String[][] {
    });
    put("Default for GNOME", new String[][] {
    { "alt F1",                   "SelectIn", "ProjectViewChangeView"},
    { "shift alt 1",              "SelectIn", "ProjectViewChangeView"},
    { "shift alt 7",              "IDEtalk.SearchUserHistory", "FindUsages"},
    { "shift alt LEFT",           "PreviousEditorTab", "Back"},
    { "shift alt RIGHT",          "NextEditorTab", "Forward"},
    });
    put("Default for KDE", new String[][] {
    { "control 1",                "FileChooser.GotoHome", "ShowErrorDescription", "DuplicatesForm.SendToLeft"},
    { "control 2",                "FileChooser.GotoProject", "Stop", "DuplicatesForm.SendToRight"},
    { "control 3",                "FindWordAtCaret", "FileChooser.GotoModule"},
    { "control 5",                "Refresh", "Rerun"},
    { "shift alt 1",              "SelectIn", "ProjectViewChangeView"},
    { "shift alt 7",              "IDEtalk.SearchUserHistory", "FindUsages"},
    { "shift alt L",              "ReformatCode", "org.jetbrains.plugins.ruby.console.LoadInIrbConsoleAction", "context.load"},
    });
    put("Eclipse", new String[][] {
    { "F2",                       "Console.TableResult.EditValue", "QuickJavaDoc", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
    { "alt ENTER",                "ShowIntentionActions", "Console.TableResult.EditValue", "DatabaseView.PropertiesAction"},
    { "F5",                       "UML.ApplyCurrentLayout", "StepInto"},
    { "TAB",                      "EditorChooseLookupItemReplace", "NextTemplateVariable", "NextParameter", "EditorIndentSelection", "EditorTab", "NextTemplateParameter", "ExpandLiveTemplateByTab"},
    { "alt DOWN",                 "ShowContent", "MoveStatementDown", "Arrangement.Rule.Match.Condition.Move.Down"},
    { "alt UP",                   "MoveStatementUp", "Arrangement.Rule.Match.Condition.Move.Up"},
    { "alt HOME",                 "ViewNavigationBar", "ShowNavBar"},
    { "control F10",              "ShowPopupMenu", "javaee.UpdateRunningApplication", "liveedit.UpdateRunningApplication"},
    { "control D",                "EditorDeleteLine", "Diff.ShowDiff", "CompareTwoFiles", "SendEOF", "FileChooser.GotoDesktop"},
    { "control L",                "Vcs.Log.FocusTextFilter", "GotoLine"},
    { "control N",                "ShowPopupMenu", "FileChooser.NewFolder"},
    { "control P",                "FileChooser.TogglePathShowing", "Print"},
    { "control R",                "RunToCursor", "Console.TableResult.Reload", "org.jetbrains.plugins.ruby.rails.console.ReloadSources"},
    { "control U",                "EvaluateExpression", "CommanderSwapPanels"},
    { "control alt DOWN",         "Console.TableResult.NextPage", "EditorDuplicateLines"},
    { "control alt E",            "Console.History.Browse", "ExecuteInPyConsoleAction", "PerforceDirect.Edit"},
    { "control alt G",            "org.jetbrains.plugins.ruby.rails.actions.generators.GeneratorsPopupAction", "Mvc.RunTarget"},
    { "shift alt D",              "hg4idea.QFold", "Debug"},
    { "shift alt G",              "RerunTests", "hg4idea.QGotoFromPatches"},
    { "shift alt L",              "IntroduceVariable", "org.jetbrains.plugins.ruby.console.LoadInIrbConsoleAction", "context.load"},
    { "shift alt P",              "hg4idea.QPushAction", "ImplementMethods"},
    { "shift alt S",              "ShowPopupMenu", "context.save"},
    { "shift alt T",              "ShowPopupMenu", "tasks.switch"},
    { "shift control DOWN",       "ResizeToolWindowDown", "MethodDown"},
    { "shift control E",          "EditSource", "RecentChangedFiles", "Graph.Faces.OpenSelectedPages"},
    { "shift control F6",         "PreviousTab", "ChangeTypeSignature"},
    { "shift control F11",        "ToggleBookmark", "FocusTracer"},
    { "shift control G",          "FindUsagesInFile", "ClassTemplateNavigation", "GoToClass"},
    { "shift control I",          "QuickImplementations", "XDebugger.Inspect"},
    { "shift control LEFT",       "EditorPreviousWordWithSelection", "ResizeToolWindowLeft", },
    { "shift control RIGHT",      "EditorNextWordWithSelection", "ResizeToolWindowRight", },
    { "shift control UP",         "ResizeToolWindowUp", "MethodUp"},
    { "shift control K",          "Vcs.Push", "FindPrevious"},
    { "shift control X",          "EditorToggleCase", "com.jetbrains.php.framework.FrameworkRunConsoleAction"},
    { "shift control U",          "ShelveChanges.UnshelveWithDialog", "EditorToggleCase"},
    { "shift control T",          "GotoClass", "GotoChangedFile"},
    });
    put("NetBeans 6.5", new String[][] {
    { "F2",                       "GotoNextError", "GuiDesigner.EditComponent", "GuiDesigner.EditGroup", "Console.TableResult.EditValue", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
    { "F4",                       "RunToCursor", "EditSource"},
    { "F5",                       "Debugger.ResumeThread", "Resume", "UML.ApplyCurrentLayout"},
    { "alt DOWN",                 "NextOccurence", "ShowContent", "Arrangement.Rule.Match.Condition.Move.Down"},
    { "alt UP",                   "PreviousOccurence", "Arrangement.Rule.Match.Condition.Move.Up"},
    { "alt INSERT",               "FileChooser.NewFolder", "Generate", "NewElement"},
    { "control 1",                "ActivateProjectToolWindow", "DuplicatesForm.SendToLeft"},
    { "control 2",                "ActivateProjectToolWindow", "FileChooser.GotoProject", "DuplicatesForm.SendToRight"},
    { "control 3",                "ActivateProjectToolWindow", "FileChooser.GotoModule"},
    { "control BACK_SPACE",       "EditorDeleteToWordStart", "ToggleDockMode"},
    { "control DIVIDE",           "CollapseRegionRecursively", "Images.Editor.ActualSize"},
    { "control D",                "EditorDuplicate", "Diff.ShowDiff", "CompareTwoFiles", "SendEOF", "FileChooser.GotoDesktop"},
    { "control M",                "Vcs.ShowMessageHistory", "Move"},
    { "control R",                "RenameElement", "Console.TableResult.Reload", "org.jetbrains.plugins.ruby.rails.console.ReloadSources"},
    { "control SLASH",            "CommentByLineComment", "Images.Editor.ActualSize"},
    { "control U",                "EditorToggleCase", "CommanderSwapPanels"},
    { "control O",                "GotoClass", "GotoChangedFile"},
    { "control PERIOD",           "GotoNextError", "EditorChooseLookupItemDot"},
    { "control alt DOWN",         "MethodDown", "Console.TableResult.NextPage"},
    { "control alt UP",           "MethodUp", "Console.TableResult.PreviousPage"},
    { "shift F4",                 "RecentFiles", "Debugger.EditTypeSource", "Vcs.ShowMessageHistory", "EditSourceInNewWindow"},
    { "shift alt F9",             "ChooseDebugConfiguration", "ValidateXml", "ValidateJsp"},
    { "shift alt D",              "ToggleFloatingMode", "hg4idea.QFold"},
    { "shift control DOWN",       "EditorDuplicate", "ResizeToolWindowDown", },
    { "shift control ENTER",      "EditorCompleteStatement", "Console.Jpa.GenerateSql"},
    { "shift control F7",         "HighlightUsagesInFile", "XDebugger.NewWatch"},
    { "shift control UP",         "EditorDuplicate", "ResizeToolWindowUp", },
    { "shift control alt P",      "Print", "Graph.Print"},
    { "shift control K",          "HippieCompletion", "Vcs.Push"},
    { "control alt E",            "Console.History.Browse", "ExecuteInPyConsoleAction", "PerforceDirect.Edit"},
    { "TAB",                      "NextTemplateVariable", "NextParameter", "EditorTab", "EditorChooseLookupItemReplace", "EditorIndentSelection", "NextTemplateParameter", "ExpandLiveTemplateByTab"},
    { "shift TAB",                "EditorUnindentSelection", "PreviousTemplateVariable", "PrevParameter", "PrevTemplateParameter"},
    });
    put("JBuilder", new String[][] {
    { "F2",                       "EditorTab", "GuiDesigner.EditComponent", "GuiDesigner.EditGroup", "Console.TableResult.EditValue", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
    { "F5",                       "ToggleBreakpointEnabled", "UML.ApplyCurrentLayout"},
    { "TAB",                      "EditorChooseLookupItemReplace", "NextTemplateVariable", "NextParameter", "EditorIndentSelection", "EmacsStyleIndent", "NextTemplateParameter", "ExpandLiveTemplateByTab"},
    { "control F6",               "PreviousEditorTab", "PreviousTab", },
    { "control L",                "Vcs.Log.FocusTextFilter", "EditorSelectLine"},
    { "control M",                "Vcs.ShowMessageHistory", "OverrideMethods", },
    { "control N",                "FileChooser.NewFolder", "GotoClass", "GotoChangedFile"},
    { "control P",                "FileChooser.TogglePathShowing", "FindInPath"},
    { "shift control A",          "SaveAll", "GotoAction"},
    { "shift control E",          "RecentChangedFiles", "ExtractMethod"},
    { "shift control ENTER",      "FindUsages", "Console.Jpa.GenerateSql"},
    { "shift control F6",         "NextTab", "ChangeTypeSignature"},
    { "shift control G",          "GotoSymbol", "ClassTemplateNavigation", "GoToClass"},
    { "control SUBTRACT",         "CollapseAll", "CollapseRegion"},
    { "control alt I",            "DatabaseView.GenerateScriptIntoConsole", "AutoIndentLines"},
    { "shift control X",          "EditorToggleShowWhitespaces", "com.jetbrains.php.framework.FrameworkRunConsoleAction"},
    });
    put("Eclipse (Mac OS X)", new String[][] {
      { "meta BACK_SPACE",          "EditorDeleteToWordStart", "$Delete"},
      { "F2",                       "Console.TableResult.EditValue", "QuickJavaDoc", "XDebugger.SetValue", "XDebugger.EditWatch", "Arrangement.Rule.Edit"},
      { "F3",                       "GotoDeclaration", "EditSource"},
      { "F5",                       "StepInto", "Console.TableResult.Reload", "UML.ApplyCurrentLayout"},
      { "alt DOWN",                 "MoveStatementDown", "Arrangement.Rule.Match.Condition.Move.Down"},
      { "alt UP",                   "MoveStatementUp", "Arrangement.Rule.Match.Condition.Move.Up"},
      { "control PERIOD",           "EditorChooseLookupItemDot", "HippieCompletion"},
      { "meta 1",                   "FileChooser.GotoHome", "ShowIntentionActions", "DuplicatesForm.SendToLeft"},
      { "meta 3",                   "FileChooser.GotoModule", "GotoAction"},
      { "meta D",                   "EditorDeleteLine", "Diff.ShowDiff", "CompareTwoFiles", "SendEOF", "FileChooser.GotoDesktop"},
      { "meta I",                   "DatabaseView.PropertiesAction", "AutoIndentLines"},
      { "meta P",                   "FileChooser.TogglePathShowing", "Print"},
      { "meta R",                   "org.jetbrains.plugins.ruby.rails.console.ReloadSources", "RunToCursor"},
      { "meta U",                   "CommanderSwapPanels", "EvaluateExpression"},
      { "meta W",                   "CloseContent", "CloseActiveTab"},
      { "shift meta T",             "GotoClass", "GotoChangedFile"},
      { "meta alt DOWN",            "Console.TableResult.NextPage", "EditorDuplicateLines"},
      { "shift meta F11",           "Run", "FocusTracer"},
      { "shift meta G",             "ClassTemplateNavigation", "GoToClass", "FindUsages"},
      { "shift meta K",             "Vcs.Push", "FindPrevious"},
      { "shift meta X",             "EditorToggleCase", "com.jetbrains.php.framework.FrameworkRunConsoleAction"},
      { "shift meta U",             "FindUsagesInFile", "ShelveChanges.UnshelveWithDialog"},
      { "control shift alt Z",      "Vcs.RollbackChangedLines", "ChangesView.Revert"},
      { "meta alt I",               "Inline", "DatabaseView.GenerateScriptIntoConsole"}
    });
  }};
  // @formatter:on

  @NonNls private static final Set<String> UNCNOWN_ACTION_IDS = ContainerUtil.set(
    "ActivateVersionControlToolWindow", "ActivateFavoritesToolWindow", "ActivateCommanderToolWindow", "ActivateDebugToolWindow",
    "ActivateFindToolWindow",
    "ActivateHierarchyToolWindow", "ActivateMessagesToolWindow", "ActivateProjectToolWindow", "ActivateRunToolWindow",
    "ActivateStructureToolWindow", "ActivateTODOToolWindow", "ActivateWebToolWindow", "ActivatePaletteToolWindow",
    "ActivateTerminalToolWindow",
    "IDEtalk.SearchUserHistory", "IDEtalk.SearchUserHistory", "IDEtalk.Rename",
    ""
  );

  @NonNls protected static final Set<String> DEFAULT_BOUND_ACTIONS = ContainerUtil.set(
    "EditorDelete"
  );
}
