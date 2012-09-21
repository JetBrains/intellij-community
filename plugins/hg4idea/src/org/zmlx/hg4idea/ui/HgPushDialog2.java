package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgPusher2;
import org.zmlx.hg4idea.command.HgTagBranch;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HgPushDialog2 extends DialogWrapper{
  private static final String DEFAULT_TARGET = "tip";
  private final Project project;

  private JPanel contentPane;
  private JTextField targetText;
  private JCheckBox revisionCheckBox;
  private JTextField revisionText;
  private JCheckBox branchCheckBox;
  private JComboBox branchComboBox;
  private JCheckBox forcePushCheckBox;
  private JCheckBox pushRepositoryCheckbox;
  private JList repositoriesList;

  private final List<HgRepositorySettings> repositorySettings = new ArrayList<HgRepositorySettings>();

  private HgRepositorySettings currentRepository;

  private boolean uiListenersActive = true;

  /**
   * Create a new dialog allowing to push the changes of all/a subset of the Mercurial repositories
   * @param project The project
   * @param repositories List of Mercurial repositories. Should not be empty
   *                     @param repositoryBranches List of List of branches. The order in the list must be the same as the order in {@code repositories}
   * @param aDefaultRepository The repository of which the settings are shown when the dialog is opened (only if included in {@code repositories}
   */
  public HgPushDialog2(Project project, @NotNull List<VirtualFile> repositories, @NotNull List<List<HgTagBranch>> repositoryBranches, VirtualFile aDefaultRepository ) {
    super(project, false);
    this.project = project;

    createRepositorySettings(repositories, repositoryBranches, aDefaultRepository);

    repositoriesList.setModel(new HgRepositorySettingsListModel(repositorySettings));
    repositoriesList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
    repositoriesList.setCellRenderer(new HgRepositorySettingsListRenderer(repositoriesList.getCellRenderer()));
    repositoriesList.addMouseListener(new HgRepositorySettingsListMouseListener());

    repositoriesList.setSelectedIndex(repositorySettings.indexOf(currentRepository));
    currentRepositoryShouldFollowListSelection();

    currentRepositorySettingsShouldFollowUIChanges();

    updateUIForCurrentRepository();

    //todo selection of checkbox in list must change selected state
    //todo change enabled state of OK button based on valid state of at least one repository
    //todo add listener to the settings to update the ui when the settings of the current repository change
    setTitle("Push repositories");
    setOKButtonText("Push selected");
    init();
  }

  private void createRepositorySettings(List<VirtualFile> repositories, List<List<HgTagBranch>> repositoryBranches, VirtualFile aDefaultRepository){
    for (int i = 0, repositoriesSize = repositories.size(); i < repositoriesSize; i++) {
      VirtualFile repo = repositories.get(i);
      HgRepositorySettings settings = new HgRepositorySettings(project, repo, repositoryBranches.get(i));
      repositorySettings.add(settings);
      if (aDefaultRepository == repo) {
        currentRepository = settings;
      }
    }
    if ( currentRepository == null ){
      currentRepository = repositorySettings.get(0);
    }
  }

  private void currentRepositoryShouldFollowListSelection(){
    repositoriesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (!(listSelectionEvent.getValueIsAdjusting())) {
          currentRepository = (HgRepositorySettings)repositoriesList.getSelectedValue();
          updateUIForCurrentRepository();
        }
      }
    });
  }

  private void currentRepositorySettingsShouldFollowUIChanges() {
    TextFieldListener textFieldListener = new TextFieldListener();
    CheckComboBoxListener checkboxListener = new CheckComboBoxListener();

    targetText.getDocument().addDocumentListener( textFieldListener );
    revisionCheckBox.addItemListener(checkboxListener);
    revisionText.getDocument().addDocumentListener(textFieldListener);
    branchCheckBox.addItemListener(checkboxListener);
    branchComboBox.addItemListener(checkboxListener);
    forcePushCheckBox.addItemListener(checkboxListener);
    pushRepositoryCheckbox.addItemListener(checkboxListener);
  }

  public List<HgRepositorySettings> getRepositorySettings() {
    return Collections.unmodifiableList(repositorySettings);
  }

  private void updateUIForCurrentRepository(){
    boolean listenersActive = uiListenersActive;
    try {
      uiListenersActive = false;
      if (currentRepository != null ) {
        pushRepositoryCheckbox.setSelected(currentRepository.isSelected());

        targetText.setText(currentRepository.getTarget());

        revisionCheckBox.setSelected(currentRepository.isRevisionSelected());
        revisionText.setText(currentRepository.getRevision());

        List<HgTagBranch> branches = currentRepository.getBranches();
        branchComboBox.setModel(new DefaultComboBoxModel(branches.toArray( new HgTagBranch[branches.size()])));
        branchComboBox.setSelectedItem(currentRepository.getBranch());
        branchCheckBox.setSelected(currentRepository.isBranchSelected());

        forcePushCheckBox.setSelected(currentRepository.isForce());
      }

      pushRepositoryCheckbox.setEnabled(currentRepository != null );
      if ( currentRepository == null ){
        pushRepositoryCheckbox.setSelected(false);
      }

      revisionText.setEnabled(revisionCheckBox.isSelected() && currentRepository != null);
      targetText.setEnabled(currentRepository != null);

      branchComboBox.setEnabled( branchCheckBox.isSelected() && currentRepository != null );
      if ( currentRepository == null ){
        branchComboBox.removeAllItems();
      }

      forcePushCheckBox.setEnabled(currentRepository != null);
      if (currentRepository == null) {
        forcePushCheckBox.setSelected(false);
      }
    }
    finally {
      uiListenersActive = listenersActive;
    }
  }

  private void updateCurrentRepositoryFromUI(){
    if ( uiListenersActive ) {
      if ( currentRepository != null ){
        currentRepository.setSelected(pushRepositoryCheckbox.isSelected());

        currentRepository.setTarget(targetText.getText());

        currentRepository.setRevisionSelected(revisionCheckBox.isSelected());
        currentRepository.setRevision(revisionText.getText());

        currentRepository.setBranchSelected(branchCheckBox.isSelected());
        currentRepository.setBranch((HgTagBranch)branchComboBox.getSelectedItem());

        currentRepository.setForce(forcePushCheckBox.isSelected());
      }
      updateUIForCurrentRepository();//update UI again to make sure the enabled state is changed as well
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.push.dialog";
  }

  private class TextFieldListener implements DocumentListener{
    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
      updateCurrentRepositoryFromUI();
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
      updateCurrentRepositoryFromUI();
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
      updateCurrentRepositoryFromUI();
    }
  }

  private class CheckComboBoxListener implements ItemListener{
    @Override
    public void itemStateChanged(ItemEvent itemEvent) {
      updateCurrentRepositoryFromUI();
    }
  }


  /**
   * Container class for a repository and it associated settings
   */
  public static class HgRepositorySettings{
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    @NotNull
    private final VirtualFile repository;
    @NotNull
    private final List<HgTagBranch> branches;
    private boolean selected = true;
    private boolean force = false;
    @Nullable
    private HgTagBranch branch=null;
    private boolean branchSelected = false;

    @NotNull
    private String revision = DEFAULT_TARGET;
    private boolean revisionSelected = false;

    @NotNull
    private String target;

    private boolean valid = true;
    private HgRepositorySettings(@NotNull Project project, @NotNull VirtualFile repository, @NotNull List<HgTagBranch> branches) {
      this.repository = repository;
      target = HgPusher2.getDefaultPushPath( project, repository);
      this.branches = Collections.unmodifiableList(branches);
      branch = branches.get(0);
    }

    /**
     * When {@code true}, the repository is marked to be pushed
     */
    public boolean isSelected() {
      return selected;
    }

    public void setSelected(boolean selected) {
      boolean old = this.selected;
      this.selected = selected;
      revalidate();
      propertyChangeSupport.firePropertyChange("selected", old, selected );
    }

    public boolean isForce() {
      return force;
    }

    public void setForce(boolean force) {
      boolean old = this.force;
      this.force = force;
      revalidate();
      propertyChangeSupport.firePropertyChange("force", old, force );
    }
    public boolean isBranchSelected() {
      return branchSelected;
    }

    public void setBranchSelected(boolean branchSelected) {
      boolean old = this.branchSelected;
      this.branchSelected = branchSelected;
      revalidate();
      propertyChangeSupport.firePropertyChange("branchSelected", old, branchSelected);
    }
    @Nullable
    public HgTagBranch getBranch() {
      return branch;
    }

    public void setBranch(@Nullable HgTagBranch branch) {
      HgTagBranch old = this.branch;
      this.branch = branch;
      revalidate();
      propertyChangeSupport.firePropertyChange("branch", old, branch);
    }
    public boolean isRevisionSelected() {
      return revisionSelected;
    }

    public void setRevisionSelected(boolean revisionSelected) {
      boolean old = this.revisionSelected;
      this.revisionSelected = revisionSelected;
      revalidate();
      propertyChangeSupport.firePropertyChange("revisionSelected", old, revisionSelected);
    }
    @NotNull
    public String getTarget() {
      return target;
    }

    public void setTarget(@NotNull String target) {
      String old = this.target;
      this.target = target;
      revalidate();
      propertyChangeSupport.firePropertyChange("target", old, target);
    }

    @NotNull
    public VirtualFile getRepository() {
      return repository;
    }

    @NotNull
    public String getRevision() {
      return revision;
    }

    public void setRevision(@NotNull String revision) {
      String old = this.revision;
      this.revision = revision;
      revalidate();
      propertyChangeSupport.firePropertyChange("revision", old, revision);
    }

    @NotNull
    public List<HgTagBranch> getBranches() {
      return branches;
    }


    public boolean isValid(){
      return valid;
    }

    private void revalidate(){
      boolean old = valid;

      valid = true;
      if(StringUtil.isEmptyOrSpaces(getTarget())){
        valid = false;
      }
      if( revisionSelected && StringUtil.isEmptyOrSpaces(revision)){
        valid=false;
      }
      if ( branchSelected && branch == null ){
        valid = false;
      }

      propertyChangeSupport.firePropertyChange("valid", old, valid );
    }

    public void addPropertyChangeListener( PropertyChangeListener propertyChangeListener ){
      propertyChangeSupport.addPropertyChangeListener(propertyChangeListener);
    }
    public void removePropertyChangeListener( PropertyChangeListener propertyChangeListener ){
      propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
    }
  }
}
