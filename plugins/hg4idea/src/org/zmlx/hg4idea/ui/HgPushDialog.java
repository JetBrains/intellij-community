// Copyright Robin Stevens
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgPusher;
import org.zmlx.hg4idea.command.HgTagBranch;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HgPushDialog extends DialogWrapper{
  private static final String ourDefaultTarget = "tip";
  private final Project myProject;

  private JPanel myContentPane;
  private JTextField myTargetText;
  private JCheckBox myRevisionCheckBox;
  private JTextField myRevisionText;
  private JCheckBox myBranchCheckBox;
  private JComboBox myBranchComboBox;
  private JCheckBox myForcePushCheckBox;
  private JCheckBox myPushRepositoryCheckbox;
  private JList myRepositoriesList;
  private JPanel myWarningMessagePanel;
  private JTextArea myWarningTextArea;
  private JLabel myWarningLabel;

  private final List<HgRepositorySettings> myRepositorySettings = new ArrayList<HgRepositorySettings>();

  private HgRepositorySettings myCurrentRepository;

  private boolean myUIListenersActive = true;//UI listeners should only react when we are not updating the UI
  private boolean mySettingsListenersActive = true;//settings listener should only react when we are not updating the setting

  /**
   * Create a new dialog allowing to push the changes of all/a subset of the Mercurial repositories
   * @param project The project
   * @param repositories List of Mercurial repositories. Should not be empty
   *                     @param repositoryBranches List of List of branches. The order in the list must be the same as the order in {@code repositories}
   * @param aDefaultRepository The repository of which the settings are shown when the dialog is opened (only if included in {@code repositories}
   */
  public HgPushDialog(Project project,
                      @NotNull List<VirtualFile> repositories,
                      @NotNull List<List<HgTagBranch>> repositoryBranches,
                      VirtualFile aDefaultRepository) {
    super(project, false);
    this.myProject = project;

    createRepositorySettings(repositories, repositoryBranches, aDefaultRepository);

    myRepositoriesList.setModel(new HgRepositorySettingsListModel(myRepositorySettings));
    myRepositoriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myRepositoriesList.setCellRenderer(new HgRepositorySettingsListRenderer(myRepositoriesList.getCellRenderer()));
    myRepositoriesList.addMouseListener(new HgRepositorySettingsListMouseListener());

    myRepositoriesList.setSelectedIndex(myRepositorySettings.indexOf(myCurrentRepository));
    currentRepositoryShouldFollowListSelection();

    currentRepositorySettingsShouldFollowUIChanges();
    uiShouldFollowCurrentRepositorySettingsChanges();

    updateUIForCurrentRepository();

    setTitle("Push repositories");
    setOKButtonText("Push selected");
    setOKActionEnabled(containsValidRepositorySettings());
    updateEnabledStateOKButtonWhenRepositoryIsChanged();

    initWarningPanel();

    init();
  }

  private void initWarningPanel(){
    initWarningMessageArea();
    myWarningLabel.setIcon(AllIcons.General.BalloonWarning);
    myWarningLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    updateWarningPanelVisibility();
  }

  private void updateWarningPanelVisibility() {
    myWarningMessagePanel.setVisible(myCurrentRepository != null && !myCurrentRepository.isValid());
  }

  private void initWarningMessageArea(){
    myWarningTextArea.setText("Invalid settings. Repository will not be pushed.");
    myWarningTextArea.setLineWrap(true);
    myWarningTextArea.setWrapStyleWord(true);
    myWarningTextArea.setEditable(false);
    myWarningTextArea.setDragEnabled(false);
    myWarningTextArea.setOpaque(false);
    myWarningTextArea.setMinimumSize(new Dimension(1, 1));
    myWarningTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
  }

  private boolean containsValidRepositorySettings(){
    for (HgRepositorySettings settings : myRepositorySettings) {
      if (settings.isValid()) {
        return true;
      }
    }
    return false;
  }
  private void updateEnabledStateOKButtonWhenRepositoryIsChanged(){
    PropertyChangeListener listener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if ( "valid".equals(evt.getPropertyName()) ){
          setOKActionEnabled(containsValidRepositorySettings());
          updateWarningPanelVisibility();
        }
      }
    };
    for (HgRepositorySettings settings : myRepositorySettings) {
      settings.addPropertyChangeListener(listener);
    }
  }
  private void createRepositorySettings(List<VirtualFile> repositories, List<List<HgTagBranch>> repositoryBranches, VirtualFile aDefaultRepository){
    for (int i = 0, repositoriesSize = repositories.size(); i < repositoriesSize; i++) {
      VirtualFile repo = repositories.get(i);
      HgRepositorySettings settings = new HgRepositorySettings(myProject, repo, repositoryBranches.get(i));
      myRepositorySettings.add(settings);
      if (aDefaultRepository == repo) {
        myCurrentRepository = settings;
      }
    }
    if ( myCurrentRepository == null ){
      myCurrentRepository = myRepositorySettings.get(0);
    }
  }

  private void currentRepositoryShouldFollowListSelection(){
    myRepositoriesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (!(listSelectionEvent.getValueIsAdjusting())) {
          myCurrentRepository = (HgRepositorySettings)myRepositoriesList.getSelectedValue();
          updateUIForCurrentRepository();
        }
      }
    });
  }

  private void currentRepositorySettingsShouldFollowUIChanges() {
    TextFieldListener textFieldListener = new TextFieldListener();
    CheckComboBoxListener checkboxListener = new CheckComboBoxListener();

    myTargetText.getDocument().addDocumentListener( textFieldListener );
    myRevisionCheckBox.addItemListener(checkboxListener);
    myRevisionText.getDocument().addDocumentListener(textFieldListener);
    myBranchCheckBox.addItemListener(checkboxListener);
    myBranchComboBox.addItemListener(checkboxListener);
    myForcePushCheckBox.addItemListener(checkboxListener);
    myPushRepositoryCheckbox.addItemListener(checkboxListener);
  }

  private void uiShouldFollowCurrentRepositorySettingsChanges(){
    PropertyChangeListener listener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (mySettingsListenersActive) {
          updateUIForCurrentRepository();
        }
      }
    };
    for (HgRepositorySettings settings : myRepositorySettings) {
      settings.addPropertyChangeListener(listener);
    }
  }

  public List<HgRepositorySettings> getRepositorySettings() {
    return Collections.unmodifiableList(myRepositorySettings);
  }

  private void updateUIForCurrentRepository(){
    boolean listenersActive = myUIListenersActive;
    try {
      myUIListenersActive = false;//deactivate listeners as we are going to update the UI
      if (myCurrentRepository != null ) {
        myPushRepositoryCheckbox.setSelected(myCurrentRepository.isSelected());

        myTargetText.setText(myCurrentRepository.getTarget());

        myRevisionCheckBox.setSelected(myCurrentRepository.isRevisionSelected());
        myRevisionText.setText(myCurrentRepository.getRevision());

        List<HgTagBranch> branches = myCurrentRepository.getBranches();
        myBranchComboBox.setModel(new DefaultComboBoxModel(branches.toArray(new HgTagBranch[branches.size()])));
        myBranchComboBox.setSelectedItem(myCurrentRepository.getBranch());
        myBranchCheckBox.setSelected(myCurrentRepository.isBranchSelected());

        myForcePushCheckBox.setSelected(myCurrentRepository.isForce());

      } else {
        myPushRepositoryCheckbox.setSelected(false);
        myBranchComboBox.removeAllItems();
        myForcePushCheckBox.setSelected(false);
      }
      updateWarningPanelVisibility();
      updateEnabledStateUIComponents();
    }
    finally {
      myUIListenersActive = listenersActive;
    }
  }

  private void updateEnabledStateUIComponents(){
    myPushRepositoryCheckbox.setEnabled(myCurrentRepository != null);
    myRevisionText.setEnabled(myRevisionCheckBox.isSelected() && myCurrentRepository != null);
    myTargetText.setEnabled(myCurrentRepository != null);
    myBranchComboBox.setEnabled(myBranchCheckBox.isSelected() && myCurrentRepository != null);
    myForcePushCheckBox.setEnabled(myCurrentRepository != null);
  }

  private void updateCurrentRepositoryFromUI(){
    boolean listenersActive = mySettingsListenersActive;
    try{
      mySettingsListenersActive = false;//deactivate listeners as wer are going to adjust the settings
      if ( myCurrentRepository != null ){
        myCurrentRepository.setSelected(myPushRepositoryCheckbox.isSelected());

        myCurrentRepository.setTarget(myTargetText.getText());

        myCurrentRepository.setRevisionSelected(myRevisionCheckBox.isSelected());
        myCurrentRepository.setRevision(myRevisionText.getText());

        myCurrentRepository.setBranchSelected(myBranchCheckBox.isSelected());
        myCurrentRepository.setBranch((HgTagBranch)myBranchComboBox.getSelectedItem());

        myCurrentRepository.setForce(myForcePushCheckBox.isSelected());
      }
      updateEnabledStateUIComponents();//update UI again to make sure the enabled state is changed as well
    } finally {
      mySettingsListenersActive = listenersActive;
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.push.dialog";
  }

  private class TextFieldListener implements DocumentListener{
    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
      if (myUIListenersActive) {
        updateCurrentRepositoryFromUI();
      }
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
      if (myUIListenersActive) {
        updateCurrentRepositoryFromUI();
      }
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
      if (myUIListenersActive) {
        updateCurrentRepositoryFromUI();
      }
    }
  }

  private class CheckComboBoxListener implements ItemListener{
    @Override
    public void itemStateChanged(ItemEvent itemEvent) {
      if (myUIListenersActive) {
        updateCurrentRepositoryFromUI();
      }
    }
  }


  /**
   * Container class for a repository and it associated settings
   */
  public static class HgRepositorySettings{
    private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
    @NotNull
    private final VirtualFile myRepository;
    @NotNull
    private final List<HgTagBranch> myBranches;
    private boolean mySelected = true;
    private boolean myForce = false;
    @Nullable
    private HgTagBranch myBranch =null;
    private boolean myBranchSelected = false;

    @NotNull
    private String myRevision = ourDefaultTarget;
    private boolean myRevisionSelected = false;

    @NotNull
    private String myTarget;

    private boolean myValid = true;
    private HgRepositorySettings(@NotNull Project project, @NotNull VirtualFile repository, @NotNull List<HgTagBranch> branches) {
      this.myRepository = repository;
      myTarget = HgPusher.getDefaultPushPath(project, repository);
      this.myBranches = Collections.unmodifiableList(branches);
      myBranch = branches.get(0);
    }

    /**
     * When {@code true}, the myRepository is marked to be pushed
     */
    public boolean isSelected() {
      return mySelected;
    }

    public void setSelected(boolean selected) {
      boolean old = this.mySelected;
      this.mySelected = selected;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("selected", old, selected);
    }

    public boolean isForce() {
      return myForce;
    }

    public void setForce(boolean force) {
      boolean old = this.myForce;
      this.myForce = force;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("force", old, force);
    }
    public boolean isBranchSelected() {
      return myBranchSelected;
    }

    public void setBranchSelected(boolean branchSelected) {
      boolean old = this.myBranchSelected;
      this.myBranchSelected = branchSelected;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("branchSelected", old, branchSelected);
    }
    @Nullable
    public HgTagBranch getBranch() {
      return myBranch;
    }

    public void setBranch(@Nullable HgTagBranch branch) {
      HgTagBranch old = this.myBranch;
      this.myBranch = branch;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("branch", old, branch);
    }
    public boolean isRevisionSelected() {
      return myRevisionSelected;
    }

    public void setRevisionSelected(boolean revisionSelected) {
      boolean old = this.myRevisionSelected;
      this.myRevisionSelected = revisionSelected;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("revisionSelected", old, revisionSelected);
    }
    @NotNull
    public String getTarget() {
      return myTarget;
    }

    public void setTarget(@NotNull String target) {
      String old = this.myTarget;
      this.myTarget = target;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("target", old, target);
    }

    @NotNull
    public VirtualFile getRepository() {
      return myRepository;
    }

    @NotNull
    public String getRevision() {
      return myRevision;
    }

    public void setRevision(@NotNull String revision) {
      String old = this.myRevision;
      this.myRevision = revision;
      revalidate();
      myPropertyChangeSupport.firePropertyChange("revision", old, revision);
    }

    @NotNull
    public List<HgTagBranch> getBranches() {
      return myBranches;
    }


    public boolean isValid(){
      return myValid;
    }

    private void revalidate(){
      boolean old = myValid;

      myValid = true;
      if(StringUtil.isEmptyOrSpaces(getTarget())){
        myValid = false;
      }
      if( myRevisionSelected && StringUtil.isEmptyOrSpaces(myRevision)){
        myValid =false;
      }
      if ( myBranchSelected && myBranch == null ){
        myValid = false;
      }

      myPropertyChangeSupport.firePropertyChange("valid", old, myValid);
    }

    public void addPropertyChangeListener( PropertyChangeListener propertyChangeListener ){
      myPropertyChangeSupport.addPropertyChangeListener(propertyChangeListener);
    }
    public void removePropertyChangeListener( PropertyChangeListener propertyChangeListener ){
      myPropertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
    }
  }
}
