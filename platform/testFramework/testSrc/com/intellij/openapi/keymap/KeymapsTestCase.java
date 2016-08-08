/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.keymap.impl.MacOSDefaultKeymap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * @author cdr
 */
public abstract class KeymapsTestCase extends PlatformTestCase {
  private static final boolean OUTPUT_TEST_DATA = false;

  public void testDuplicateShortcuts() {
    StringBuilder failMessage = new StringBuilder();
    Map<String, Map<String, List<String>>> knownDuplicates = getKnownDuplicates();

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      String failure = checkDuplicatesInKeymap(keymap, knownDuplicates);
      if (failMessage.length() > 0) failMessage.append("\n");
      failMessage.append(failure);
    }
    if (failMessage.length() > 0) {
      TestCase.fail(failMessage +
                    "\n" +
                    "Please specify 'use-shortcut-of' attribute for your action if it is similar to another action (but it won't appear in Settings/Keymap),\n" +
                    "reassign shortcut or, if absolutely must, modify the 'known duplicates list'");
    }
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
    { "control DOWN",             "EditorScrollDown", "EditorLookupDown"},
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
    { "control UP",               "EditorScrollUp", "EditorLookupUp"},
    { "control alt A",            "ChangesView.AddUnversioned", "Diagram.DeselectAll"},
    { "control alt E",            "PerforceDirect.Edit", "Console.History.Browse"},
    { "control alt DOWN",         "NextOccurence", "Console.TableResult.NextPage"},
    { "control alt G",            "org.jetbrains.plugins.ruby.rails.actions.generators.GeneratorsPopupAction", "Mvc.RunTarget"},
    { "control alt R",            "org.jetbrains.plugins.ruby.tasks.rake.actions.RakeTasksPopupAction", "Django.RunManageTaskAction"},
    { "control alt UP",           "PreviousOccurence", "Console.TableResult.PreviousPage"},
    { "control alt N",            "Inline", "Console.TableResult.SetNull"},
    { "control MINUS",            "CollapseAll", "CollapseRegion"},
    { "control PERIOD",           "EditorChooseLookupItemDot", "CollapseSelection"},
    { "shift DELETE",             "$Cut", "Maven.Uml.Exclude"},
    { "shift ENTER",              "EditorStartNewLine", "Console.TableResult.EditValueMaximized"},
    { "shift F4",                 "Debugger.EditTypeSource", "EditSourceInNewWindow"},
    { "shift F7",                 "PreviousDiff", "SmartStepInto"},
    { "shift TAB",                "PreviousTemplateVariable", "PrevParameter", "EditorUnindentSelection", "PrevTemplateParameter"},
    { "shift alt L",              "org.jetbrains.plugins.ruby.console.LoadInIrbConsoleAction", "context.load"},
    { "shift alt T",              "tasks.switch", "tasks.switch.toolbar"},
    { "shift control D",          "TagDocumentationNavigation", "Diff.ShowSettingsPopup", "Uml.ShowDiff"},
    { "shift control DOWN",       "ResizeToolWindowDown", "MoveStatementDown"},
    { "shift control ENTER",      "EditorChooseLookupItemCompleteStatement", "EditorCompleteStatement", "Console.Jpa.GenerateSql"},
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
    { "control UP",               "EditorBackwardParagraph", "EditorLookupUp"},
    { "control DOWN",             "EditorForwardParagraph", "EditorLookupDown"},
    { "control alt A",            "MethodUp", "ChangesView.AddUnversioned", "Diagram.DeselectAll"},
    { "control alt E",            "MethodDown", "PerforceDirect.Edit", "Console.History.Browse"},
    { "control alt G",            "GotoDeclaration", "org.jetbrains.plugins.ruby.rails.actions.generators.GeneratorsPopupAction", "Mvc.RunTarget"},
    { "control alt S",            "ShowSettings", "Find"},
    { "shift DELETE",             "$Cut", "Maven.Uml.Exclude"},
    { "shift alt S",              "FindUsages", "context.save"},
    { "shift alt G",              "GotoChangedFile", "GotoClass", "hg4idea.QGotoFromPatches"},
    { "shift alt P",              "ParameterInfo", "hg4idea.QPushAction"},
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
    { "control F11",              "Rerun", "ToggleBookmarkWithMnemonic"},
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
    { "shift alt T",              "ShowPopupMenu", "tasks.switch", "tasks.switch.toolbar"},
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
    { "shift control ENTER",      "EditorChooseLookupItemCompleteStatement", "EditorCompleteStatement", "Console.Jpa.GenerateSql"},
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
    { "shift control ENTER",      "EditorChooseLookupItemCompleteStatement", "FindUsages", "Console.Jpa.GenerateSql"},
    { "shift control F6",         "NextTab", "ChangeTypeSignature"},
    { "shift control G",          "GotoSymbol", "ClassTemplateNavigation", "GoToClass"},
    { "control SUBTRACT",         "CollapseAll", "CollapseRegion"},
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
    });
  }};
  // @formatter:on

  private Map<String, Map<String, List<String>>> getKnownDuplicates() {
    Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
    collectKnownDuplicates(result);
    return result;
  }

  protected void collectKnownDuplicates(Map<String, Map<String, List<String>>> result) {
    appendKnownDuplicates(result, DEFAULT_DUPLICATES);
  }

  protected static void appendKnownDuplicates(Map<String, Map<String, List<String>>> result, Map<String, String[][]> duplicates) {
    for (Map.Entry<String, String[][]> eachKeymap : duplicates.entrySet()) {
      String keymapName = eachKeymap.getKey();

      Map<String, List<String>> mapping = result.get(keymapName);
      if (mapping == null) {
        result.put(keymapName, mapping = new LinkedHashMap<>());
      }

      for (String[] shortcuts : eachKeymap.getValue()) {
        TestCase.assertTrue("known duplicates list entry for '" + keymapName + "' must not contain empty array",
                            shortcuts.length > 0);
        TestCase.assertTrue("known duplicates list entry for '" + keymapName + "', shortcut '" + shortcuts[0] +
                            "' must contain at least two conflicting action ids",
                            shortcuts.length > 2);
        mapping.put(shortcuts[0], ContainerUtil.newArrayList(shortcuts, 1, shortcuts.length));
      }
    }
  }

  @NotNull
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String checkDuplicatesInKeymap(Keymap keymap, Map<String, Map<String, List<String>>> allKnownDuplicates) {
    Set<Shortcut> shortcuts = new LinkedHashSet<>();
    Set<String> aids = new THashSet<>(Arrays.asList(keymap.getActionIds()));
    removeBoundActionIds(aids);

    nextId:
    for (String id : aids) {

      Map<String, List<String>> knownDuplicates = allKnownDuplicates.get(keymap.getName());
      if (knownDuplicates != null) {
        for (List<String> actionsMapping : knownDuplicates.values()) {
          for (String eachAction : actionsMapping) {
            if (eachAction.equals(id)) continue nextId;
          }
        }
      }

      for (Shortcut shortcut : keymap.getShortcuts(id)) {
        if (!(shortcut instanceof KeyboardShortcut)) {
          continue;
        }
        shortcuts.add(shortcut);
      }
    }
    List<Shortcut> sorted = new ArrayList<>(shortcuts);
    Collections.sort(sorted, (o1, o2) -> getText(o1).compareTo(getText(o2)));

    if (OUTPUT_TEST_DATA) {
      System.out.println("put(\"" + keymap.getName() + "\", new String[][] {");
    }
    else {
      System.out.println(keymap.getName());
    }
    StringBuilder failMessage = new StringBuilder();
    for (Shortcut shortcut : sorted) {
      if (!(shortcut instanceof KeyboardShortcut)) {
        continue;
      }
      Set<String> ids = new THashSet<>(Arrays.asList(keymap.getActionIds(shortcut)));
      removeBoundActionIds(ids);
      if (ids.size() == 1) continue;
      Keymap parent = keymap.getParent();
      if (parent != null) {
        // ignore duplicates from default keymap
        boolean differFromParent = false;
        for (String id : ids) {
          Shortcut[] here = keymap.getShortcuts(id);
          Shortcut[] there = parent.getShortcuts(id);
          if (keymap.getName().startsWith("Mac")) convertMac(there);
          if (!new HashSet<>(Arrays.asList(here)).equals(new HashSet<>(Arrays.asList(there)))) {
            differFromParent = true;
            break;
          }
        }
        if (!differFromParent) continue;
      }

      String def = "{ "
                   + "\"" + getText(shortcut) + "\","
                   + StringUtil.repeatSymbol(' ', 25- getText(shortcut).length())
                   + StringUtil.join(ids, StringUtil.QUOTER, ", ")
                   + "},";
      if (OUTPUT_TEST_DATA) {
        System.out.println(def);
      }
      else {
        if (failMessage.length() == 0)  {
          failMessage.append("Shortcut '").append(getText(shortcut)).append("' conflicts found in keymap '")
            .append(keymap.getName()).append("':\n");
        }
        failMessage.append(def).append("\n");
      }
    }
    if (OUTPUT_TEST_DATA) {
      System.out.println("});");
    }
    return failMessage.toString();
  }

  private static void removeBoundActionIds(Set<String> aids) {
    // explicitly bound to another action
    for (Iterator<String> it = aids.iterator(); it.hasNext();) {
      String id = it.next();
      String sourceId = KeymapManagerEx.getInstanceEx().getActionBinding(id);
      if (sourceId != null) {
        it.remove();
      }
    }
  }

  @NonNls private static final Set<String> unknownActionIds = new THashSet<>(Arrays.asList(
    "ActivateVersionControlToolWindow", "ActivateFavoritesToolWindow", "ActivateCommanderToolWindow", "ActivateDebugToolWindow",
    "ActivateFindToolWindow",
    "ActivateHierarchyToolWindow", "ActivateMessagesToolWindow", "ActivateProjectToolWindow", "ActivateRunToolWindow",
    "ActivateStructureToolWindow", "ActivateTODOToolWindow", "ActivateWebToolWindow", "ActivatePaletteToolWindow",
    "ActivateTerminalToolWindow",
    "IDEtalk.SearchUserHistory", "IDEtalk.SearchUserHistory", "IDEtalk.Rename",
    ""
  ));

  protected void collectUnknownActions(Set<String> result) {
    result.addAll(unknownActionIds);
  }

  public void testValidActionIds() {
    THashSet<String> unknownActions = new THashSet<>();
    collectUnknownActions(unknownActions);

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<String, List<String>> missingActions = new FactoryMap<String, List<String>>() {
      @Override
      protected Map<String, List<String>> createMap() {
        return new LinkedHashMap<>();
      }

      @Nullable
      @Override
      protected List<String> create(String key) {
        return new ArrayList<>();
      }
    };
    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      String[] ids = keymap.getActionIds();
      Arrays.sort(ids);
      Set<String> noDuplicates = new LinkedHashSet<>(Arrays.asList(ids));
      TestCase.assertEquals(new ArrayList<>(Arrays.asList(ids)), new ArrayList<>(noDuplicates));
      for (String cid : ids) {
        if (unknownActions.contains(cid)) continue;
        AnAction action = ActionManager.getInstance().getAction(cid);
        if (action == null) {
          if (OUTPUT_TEST_DATA) {
            System.out.print("\""+cid+"\", ");
          }
          else {
            missingActions.get(keymap.getName()).add(cid);
          }
        }
      }
    }

    List<String> reappearedAction = new ArrayList<>();
    for (String id : unknownActions) {
      AnAction action = ActionManager.getInstance().getAction(id);
      if (action != null) {
        reappearedAction.add(id);
      }
    }

    if (!missingActions.isEmpty() || !reappearedAction.isEmpty()) {
      StringBuilder message = new StringBuilder();
      if (!missingActions.isEmpty()) {
        for (Map.Entry<String, List<String>> keymapAndActions : missingActions.entrySet()) {
          message.append("Unknown actions in keymap ").append(keymapAndActions.getKey()).append(", add them to unknown actions list:\n");
          for (String action : keymapAndActions.getValue()) {
            message.append("\"").append(action).append("\",").append("\n");
          }
        }
      }
      if (!reappearedAction.isEmpty()) {
        message.append("The following actions have reappeared, remove them from unknown action list:\n");
        for (String action : reappearedAction) {
          message.append(action).append("\n");
        }
      }
      fail("\n" + message);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void testIdsListIsConsistent() {
    Map<String, Map<String, List<String>>> duplicates = getKnownDuplicates();

    THashSet<String> allMaps =
      new THashSet<>(ContainerUtil.map(KeymapManagerEx.getInstanceEx().getAllKeymaps(), keymap -> keymap.getName()));
    TestCase.assertTrue("Modify 'known duplicates list' test data. Keymaps were added: " +
                        ContainerUtil.subtract(allMaps, duplicates.keySet()),
                        ContainerUtil.subtract(allMaps, duplicates.keySet()).isEmpty()
    );
    TestCase.assertTrue("Modify 'known duplicates list' test data. Keymaps were removed: " +
                        ContainerUtil.subtract(duplicates.keySet(), allMaps),
                        ContainerUtil.subtract(duplicates.keySet(), allMaps).isEmpty()
    );

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    Map<Keymap, List<Shortcut>> reassignedShortcuts = new FactoryMap<Keymap, List<Shortcut>>() {
      @Override
      protected Map<Keymap, List<Shortcut>> createMap() {
        return new LinkedHashMap<>();
      }

      @Nullable
      @Override
      protected List<Shortcut> create(Keymap key) {
        return new ArrayList<>();
      }
    };
    for (String name : duplicates.keySet()) {
      Keymap keymap = KeymapManagerEx.getInstanceEx().getKeymap(name);
      TestCase.assertNotNull("KeyMap " + name + " not found", keymap);
      Map<String, List<String>> duplicateIdsList = duplicates.get(name);
      Set<String> mentionedShortcuts = new THashSet<>();
      for (Map.Entry<String, List<String>> shortcutMappings : duplicateIdsList.entrySet()) {

        String shortcutString = shortcutMappings.getKey();
        if (!mentionedShortcuts.add(shortcutString)) {
          TestCase.fail("Shortcut '" + shortcutString + "' duplicate in keymap '" + keymap + "'. Please modify 'known duplicates list'");
        }
        Shortcut shortcut = parse(shortcutString);
        String[] ids = keymap.getActionIds(shortcut);
        Set<String> actualSc = new HashSet<>(Arrays.asList(ids));

        removeBoundActionIds(actualSc);

        Set<String> expectedSc = new HashSet<>(shortcutMappings.getValue());
        for (String s : actualSc) {
          if (!expectedSc.contains(s)) {
            reassignedShortcuts.get(keymap).add(shortcut);
          }
        }
        for (String s : expectedSc) {
          if (!actualSc.contains(s)) {
            System.out.println("Expected action '" + s + "' does not reassign shortcut " + getText(shortcut) + " in keymap " + keymap + " or is not registered");
          }
        }

      }
    }
    if (!reassignedShortcuts.isEmpty()) {
      StringBuilder message = new StringBuilder();
      for (Map.Entry<Keymap, List<Shortcut>> keymapToShortcuts : reassignedShortcuts.entrySet()) {
        Keymap keymap = keymapToShortcuts.getKey();
        message.append("The following shortcuts was reassigned in keymap ").append(keymap.getName())
          .append(". Please modify known duplicates list:\n");
        for (Shortcut eachShortcut : keymapToShortcuts.getValue()) {
          message.append(" { ").append(StringUtil.wrapWithDoubleQuote(getText(eachShortcut))).append(",\t")
            .append(StringUtil.join(keymap.getActionIds(eachShortcut), s -> StringUtil.wrapWithDoubleQuote(s), ", "))
            .append("},\n");
        }
      }
      TestCase.fail("\n" + message.toString());
    }
  }

  private static Shortcut parse(String s) {
    String[] sc = s.split(",");
    KeyStroke fst = ActionManagerEx.getKeyStroke(sc[0]);
    assert fst != null : s;
    KeyStroke snd = null;
    if (sc.length == 2) {
      snd = ActionManagerEx.getKeyStroke(sc[1]);
    }
    return new KeyboardShortcut(fst, snd);
  }

  private static String getText(Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      KeyStroke fst = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      String s = KeymapImpl.getKeyShortcutString(fst);

      KeyStroke snd = ((KeyboardShortcut)shortcut).getSecondKeyStroke();
      if (snd != null) {
        s += "," + KeymapImpl.getKeyShortcutString(snd);
      }
      return s;
    }
    return KeymapUtil.getShortcutText(shortcut);
  }

  private static void convertMac(Shortcut[] there) {
    for (int i = 0; i < there.length; i++) {
      there[i] = MacOSDefaultKeymap.convertShortcutFromParent(there[i]);
    }
  }

  private static final Set<String> LINUX_KEYMAPS = ContainerUtil.newHashSet("Default for XWin", "Default for GNOME", "Default for KDE");

  public void testLinuxShortcuts() {
    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (LINUX_KEYMAPS.contains(keymap.getName())) {
        checkLinuxKeymap(keymap);
      }
    }
  }

  private static void checkLinuxKeymap(final Keymap keymap) {
    for (String actionId : keymap.getActionIds()) {
      for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
        if (shortcut instanceof KeyboardShortcut) {
          checkCtrlAltFn(keymap, shortcut, ((KeyboardShortcut)shortcut).getFirstKeyStroke());
          checkCtrlAltFn(keymap, shortcut, ((KeyboardShortcut)shortcut).getSecondKeyStroke());
        }
      }
    }
  }

  private static void checkCtrlAltFn(final Keymap keymap, final Shortcut shortcut, final KeyStroke stroke) {
    if (stroke != null) {
      final int modifiers = stroke.getModifiers();
      final int keyCode = stroke.getKeyCode();
      if (KeyEvent.VK_F1 <= keyCode && keyCode <= KeyEvent.VK_F12 &&
          (modifiers & InputEvent.CTRL_MASK) != 0 && (modifiers & InputEvent.ALT_MASK) != 0 && (modifiers & InputEvent.SHIFT_MASK) == 0) {
        final String message = "Invalid shortcut '" + shortcut + "' for action(s) " + Arrays.asList(keymap.getActionIds(shortcut)) +
                               " in keymap '" + keymap.getName() + "' " +
                               "(Ctrl-Alt-Fn shortcuts switch Linux virtual terminals (causes newbie panic), " +
                               "so either assign another shortcut, or remove it; see Keymap_XWin.xml for reference).";
        TestCase.fail(message);
      }
    }
  }
}
