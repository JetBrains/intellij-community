/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class GradleConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  @NonNls public static final String HELP_TOPIC = "reference.settingsdialog.project.gradle";

  private static final long BALLOON_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final GradleInstallationManager myLibraryManager;
  private final Project                   myProject;

  private GradleHomeSettingType myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;

  private JComponent        myComponent;
  private NamePathComponent myGradleHomeComponent;
  private boolean           myPathManuallyModified;
  private boolean           myShowBalloonIfNecessary;

  public GradleConfigurable(@Nullable Project project) {
    this(project, ServiceManager.getService(GradleInstallationManager.class));
  }

  public GradleConfigurable(@Nullable Project project, @NotNull GradleInstallationManager gradleInstallationManager) {
    myLibraryManager = gradleInstallationManager;
    myProject = project;
    doCreateComponent();
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      doCreateComponent();
    }
    return myComponent;
  }

  private void doCreateComponent() {
    myComponent = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        if (!myShowBalloonIfNecessary) {
          return;
        }
        myShowBalloonIfNecessary = false;
        MessageType messageType = null;
        switch (myGradleHomeSettingType) {
          case DEDUCED: messageType = MessageType.INFO; break;
          case EXPLICIT_INCORRECT:
          case UNKNOWN: messageType = MessageType.ERROR; break;
          default:
        }
        if (messageType != null) {
          new DelayedBalloonInfo(messageType, myGradleHomeSettingType).run();
        }
      }
    };
    myComponent.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (!"ancestor".equals(evt.getPropertyName())) {
          return;
        }
        
        // Configure the balloon to show on initial configurable drawing.
        myShowBalloonIfNecessary = evt.getNewValue() != null && evt.getOldValue() == null;

        if (evt.getNewValue() == null && evt.getOldValue() != null) {
          // Cancel delayed balloons when the configurable is hidden.
          myAlarm.cancelAllRequests();
        }
      }
    });
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;

    myGradleHomeComponent = new NamePathComponent(
      "", GradleBundle.message("gradle.import.text.home.path"), GradleBundle.message("gradle.import.text.home.path"), "",
      false,
      false
    );
    myGradleHomeComponent.getPathComponent().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        useNormalColorForPath();
        myPathManuallyModified = true;
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        useNormalColorForPath();
        myPathManuallyModified = true;
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
    myGradleHomeComponent.setNameComponentVisible(false);
    myComponent.add(myGradleHomeComponent, constraints);
    myComponent.add(Box.createVerticalGlue());
  }

  @Override
  public boolean isModified() {
    if (myProject == null || !myPathManuallyModified) {
      return false;
    }
    String newPath = myGradleHomeComponent.getPath();
    String oldPath = GradleSettings.getInstance(myProject).getGradleHome();
    boolean modified = newPath == null ? oldPath == null : !newPath.equals(oldPath);
    if (modified) {
      useNormalColorForPath();
    }
    return modified;
  }

  @Override
  public void apply() {
    if (myProject == null) {
      return;
    }
    useNormalColorForPath();
    String path = myGradleHomeComponent.getPath();
    GradleSettings.applyGradleHome(path, myProject);

    if (isValidGradleHome(path)) {
      myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_CORRECT;
      // There is a possible case that user defines gradle home for particular open project. We want to apply that value
      // to the default project as well if it's still non-defined.
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      if (defaultProject != myProject && !isValidGradleHome(GradleSettings.getInstance(defaultProject).getGradleHome())) {
        GradleSettings.applyGradleHome(path, defaultProject);
      }
      return;
    }

    if (StringUtil.isEmpty(path)) {
      myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
    }
    else {
      myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_INCORRECT;
      new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
    }
  }

  private boolean isValidGradleHome(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return false;
    }
    assert path != null;
    return myLibraryManager.isGradleSdkHome(new File(path));
  }
  
  @Override
  public void reset() {
    if (myProject == null) {
      return;
    }
    useNormalColorForPath();
    myPathManuallyModified = false;
    String valueToUse = GradleSettings.getInstance(myProject).getGradleHome();
    if (StringUtil.isEmpty(valueToUse)) {
      valueToUse = GradleSettings.getInstance(ProjectManager.getInstance().getDefaultProject()).getGradleHome();
    }
    if (!StringUtil.isEmpty(valueToUse)) {
      myGradleHomeSettingType = myLibraryManager.isGradleSdkHome(new File(valueToUse)) ?
                                GradleHomeSettingType.EXPLICIT_CORRECT :
                                GradleHomeSettingType.EXPLICIT_INCORRECT;
      if (myGradleHomeSettingType == GradleHomeSettingType.EXPLICIT_INCORRECT) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType, 0).run();
      }
      else {
        myAlarm.cancelAllRequests();
      }
      myGradleHomeComponent.setPath(valueToUse);
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
    deduceGradleHomeIfPossible();
  }

  private void useNormalColorForPath() {
    myGradleHomeComponent.getPathComponent().setForeground(UIManager.getColor("TextField.foreground"));
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    File gradleHome = myLibraryManager.getGradleHome(myProject);
    if (gradleHome == null) {
      new DelayedBalloonInfo(MessageType.WARNING, GradleHomeSettingType.UNKNOWN).run();
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.DEDUCED;
    new DelayedBalloonInfo(MessageType.INFO, GradleHomeSettingType.DEDUCED).run();
    if (myGradleHomeComponent != null) {
      myGradleHomeComponent.setPath(gradleHome.getPath());
      myGradleHomeComponent.getPathComponent().setForeground(UIManager.getColor("TextField.inactiveForeground"));
      myPathManuallyModified = false;
    }
  }

  @Override
  public void disposeUIResources() {
    myComponent = null;
    myGradleHomeComponent = null;
    myPathManuallyModified = false;
  }

  @NotNull
  public String getHelpTopic() {
    return HELP_TOPIC;
  }

  /**
   * @return UI component that manages path to the local gradle distribution to use
   */
  @NotNull
  public NamePathComponent getGradleHomeComponent() {
    if (myGradleHomeComponent == null) {
      createComponent();
    }
    return myGradleHomeComponent;
  }

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  @NotNull
  public GradleHomeSettingType getCurrentGradleHomeSettingType() {
    String path = myGradleHomeComponent.getPath();
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      GradleLog.LOG.info(String.format("Checking 'gradle home' status. Manually entered value is '%s'", path));
    }
    if (path == null || StringUtil.isEmpty(path.trim())) {
      return GradleHomeSettingType.UNKNOWN;
    }
    if (isModified()) {
      return myLibraryManager.isGradleSdkHome(new File(path)) ? GradleHomeSettingType.EXPLICIT_CORRECT
                                                              : GradleHomeSettingType.EXPLICIT_INCORRECT;
    }
    return myGradleHomeSettingType;
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final String      myText;
    private final long        myTriggerTime;

    DelayedBalloonInfo(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType) {
      this(messageType, settingType, BALLOON_DELAY_MILLIS);
    }
    
    DelayedBalloonInfo(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType, long delayMillis) {
      myMessageType = messageType;
      myText = settingType.getDescription();
      myTriggerTime = System.currentTimeMillis() + delayMillis;
    }

    @Override
    public void run() {
      long diff = myTriggerTime - System.currentTimeMillis();
      if (diff > 0) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, diff);
        return;
      }
      if (myGradleHomeComponent == null) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, 200);
        return;
      }
      if (!myGradleHomeComponent.getPathComponent().isShowing()) {
        // Don't schedule the balloon if the configurable is hidden.
        return;
      }
      GradleUtil.showBalloon(myGradleHomeComponent.getPathComponent(), myMessageType, myText);
    }
  }
}
