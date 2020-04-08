// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.IdeUICustomization;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class SingleConfigurableEditor extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(SingleConfigurableEditor.class);
  private Project myProject;
  private Configurable myConfigurable;
  private JComponent myCenterPanel;
  private final String myDimensionKey;
  private final boolean myShowApplyButton;
  private boolean mySaveAllOnClose;

  public SingleConfigurableEditor(@Nullable Project project,
                                  @NotNull Configurable configurable,
                                  @NonNls String dimensionKey,
                                  final boolean showApplyButton,
                                  @NotNull IdeModalityType ideModalityType) {
    super(project, true, ideModalityType);
    myDimensionKey = dimensionKey;
    myShowApplyButton = showApplyButton;
    String title = createTitleString(configurable);
    if (project != null && project.isDefault()) {
      title = IdeUICustomization.getInstance().projectMessage("title.for.new.projects", title);
    }

    setTitle(title);

    myProject = project;
    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  public SingleConfigurableEditor(Component parent,
                                  @NotNull Configurable configurable,
                                  String dimensionServiceKey,
                                  final boolean showApplyButton,
                                  final IdeModalityType ideModalityType) {
    super(parent, true);
    myDimensionKey = dimensionServiceKey;
    myShowApplyButton = showApplyButton;
    setTitle(createTitleString(configurable));

    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  public SingleConfigurableEditor(@Nullable Project project,
                                  Configurable configurable,
                                  @NonNls String dimensionKey,
                                  final boolean showApplyButton) {
    this(project, configurable, dimensionKey, showApplyButton, IdeModalityType.IDE);
  }

  public SingleConfigurableEditor(Component parent,
                                  Configurable configurable,
                                  String dimensionServiceKey,
                                  final boolean showApplyButton) {
    this(parent, configurable, dimensionServiceKey, showApplyButton, IdeModalityType.IDE);
  }

  public SingleConfigurableEditor(@Nullable Project project, Configurable configurable, @NonNls String dimensionKey, @NotNull IdeModalityType ideModalityType) {
    this(project, configurable, dimensionKey, true, ideModalityType);
  }

  public SingleConfigurableEditor(@Nullable Project project, Configurable configurable, @NonNls String dimensionKey) {
    this(project, configurable, dimensionKey, true);
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable, String dimensionServiceKey) {
    this(parent, configurable, dimensionServiceKey, true);
  }

  public SingleConfigurableEditor(@Nullable Project project, Configurable configurable, @NotNull IdeModalityType ideModalityType) {
    this(project, configurable, ShowSettingsUtilImpl.createDimensionKey(configurable), ideModalityType);
  }

  public SingleConfigurableEditor(@Nullable Project project, Configurable configurable) {
    this(project, configurable, ShowSettingsUtilImpl.createDimensionKey(configurable));
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable) {
    this(parent, configurable, ShowSettingsUtilImpl.createDimensionKey(configurable));
  }

  public Configurable getConfigurable() {
    return myConfigurable;
  }

  public Project getProject() {
    return myProject;
  }

  private static String createTitleString(@NotNull Configurable configurable) {
    String displayName = configurable.getDisplayName();
    LOG.assertTrue(displayName != null, configurable.getClass().getName());
    return displayName.replaceAll("\n", " ");
  }

  @Override
  protected String getDimensionServiceKey() {
    if (myDimensionKey == null) {
      return super.getDimensionServiceKey();
    }
    else {
      return myDimensionKey;
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getOKAction());
    actions.add(getCancelAction());
    if (myShowApplyButton) {
      actions.add(new ApplyAction());
    }
    if (getHelpId() != null) {
      actions.add(getHelpAction());
    }
    return actions.toArray(new Action[0]);
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myConfigurable.getHelpTopic();
  }

  @Override
  protected void doOKAction() {
    try {
      if (myConfigurable.isModified()) {
        myConfigurable.apply();
        mySaveAllOnClose = true;
      }
    }
    catch (ConfigurationException e) {
      if (e.getMessage() != null) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(getRootPane(), e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
      }
      return;
    }

    super.doOKAction();
  }

  protected static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }

  protected class ApplyAction extends AbstractAction {
    private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public ApplyAction() {
      super(CommonBundle.getApplyButtonText());
      final Runnable updateRequest = new Runnable() {
        @Override
        public void run() {
          if (!isShowing()) return;
          try {
            setEnabled(myConfigurable != null && myConfigurable.isModified());
          }
          catch (IndexNotReadyException ignored) {
          }
          addUpdateRequest(this);
        }
      };

      // invokeLater necessary to make sure dialog is already shown so we calculate modality state correctly.
      SwingUtilities.invokeLater(() -> {
        // schedule if not already disposed
        if (myConfigurable != null) addUpdateRequest(updateRequest);
      });
    }

    private void addUpdateRequest(final Runnable updateRequest) {
      myUpdateAlarm.addRequest(updateRequest, 500, ModalityState.stateForComponent(getWindow()));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      if (myPerformAction) return;
      try {
        myPerformAction = true;
        if (myConfigurable.isModified()) {
          myConfigurable.apply();
          mySaveAllOnClose = true;
          setCancelButtonText(CommonBundle.getCloseButtonText());
        }
      }
      catch (ConfigurationException e) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(getRootPane(), e.getMessage(), e.getTitle(),
                                     Messages.getErrorIcon());
        }
      } finally {
        myPerformAction = false;
      }
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    myCenterPanel = myConfigurable.createComponent();
    return myCenterPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    Configurable configurable = myConfigurable;
    JComponent preferred = configurable == null ? null : configurable.getPreferredFocusedComponent();
    return preferred == null ? IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCenterPanel) : preferred;
  }

  @Override
  public void dispose() {
    super.dispose();
    myConfigurable.disposeUIResources();
    myConfigurable = null;

    if (mySaveAllOnClose) {
      SaveAndSyncHandler.getInstance().scheduleSaveDocumentsAndProjectsAndApp(myProject);
    }
  }
}
