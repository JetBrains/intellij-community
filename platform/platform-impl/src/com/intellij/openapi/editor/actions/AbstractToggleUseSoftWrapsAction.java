// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SettingsImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Provides common functionality for {@code 'toggle soft wraps usage'} actions.
 */
public abstract class AbstractToggleUseSoftWrapsAction extends ToggleAction implements DumbAware, LightEditCompatible,
                                                                                       ActionRemoteBehaviorSpecification.Frontend {

  private final SoftWrapAppliancePlaces myAppliancePlace;
  private final boolean myGlobal;

  /**
   * Creates new {@code AbstractToggleUseSoftWrapsAction} object.
   * 
   * @param appliancePlace    defines type of the place where soft wraps are applied
   * @param global            indicates if soft wraps should be changed for the current editor only or for the all editors
   *                          used at the target appliance place
   */
  public AbstractToggleUseSoftWrapsAction(@NotNull SoftWrapAppliancePlaces appliancePlace, boolean global) {
    myAppliancePlace = appliancePlace;
    myGlobal = global;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (myGlobal) {
      Editor editor = getEditor(e);
      if (editor != null) {
        EditorSettings settings = editor.getSettings();
        if (settings instanceof SettingsImpl && ((SettingsImpl)settings).getSoftWrapAppliancePlace() != myAppliancePlace) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
      }
    }
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    if (myGlobal) {
      boolean selected = EditorSettingsExternalizable.getInstance().isUseSoftWraps(myAppliancePlace);
      selected |= (editor != null && Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS)));
      return selected;
    }
    return editor != null && editor.getSettings().isUseSoftWraps();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    if (editor == null) {
      return;
    }

    toggleSoftWraps(editor, myGlobal && !Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS))
                            ? myAppliancePlace
                            : null, state);
  }

  public static void toggleSoftWraps(@NotNull Editor editor, @Nullable SoftWrapAppliancePlaces places, boolean state) {
    Point point = editor.getScrollingModel().getVisibleArea().getLocation();
    LogicalPosition anchorPosition = editor.xyToLogicalPosition(point);
    int intraLineShift = point.y - editor.logicalPositionToXY(anchorPosition).y;

    if (places != null) {
      EditorSettingsExternalizable.getInstance().setUseSoftWraps(state, places);
      EditorFactory.getInstance().refreshAllEditors();
    }
    if (editor.getSettings().isUseSoftWraps() != state) {
      editor.getSettings().setUseSoftWraps(state);
      if (!state && Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS))) {
        editor.putUserData(EditorImpl.FORCED_SOFT_WRAPS, Boolean.FALSE);
      }
    }

    editor.getScrollingModel().disableAnimation();
    editor.getScrollingModel().scrollVertically(editor.logicalPositionToXY(anchorPosition).y + intraLineShift);
    editor.getScrollingModel().enableAnimation();
  }

  protected @Nullable Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }
}
