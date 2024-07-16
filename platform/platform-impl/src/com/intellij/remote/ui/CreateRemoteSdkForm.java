// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.RemoteSdkException;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.remote.ext.*;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.StatusPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.List;
import java.util.*;

public abstract class CreateRemoteSdkForm<T extends RemoteSdkAdditionalData> extends JPanel implements RemoteSdkEditorForm, Disposable {
  private JPanel myMainPanel;
  private JBLabel myInterpreterPathLabel;
  protected TextFieldWithBrowseButton myInterpreterPathField;
  protected TextFieldWithBrowseButton myHelpersPathField;
  private JTextField myNameField;
  private JBLabel myNameLabel;
  private JBLabel myHelpersPathLabel;
  private JPanel myStatusPanelHolder;
  private final StatusPanel myStatusPanel;

  private JPanel myRadioPanel;
  private JPanel myTypesPanel;
  private JPanel myRunAsRootViaSudoPanel;
  private JBCheckBox myRunAsRootViaSudoJBCheckBox;
  private JPanel myRunAsRootViaSudoHelpPanel;
  private ButtonGroup myTypeButtonGroup;
  private boolean myNameVisible;

  private final Project myProject;
  private final @NotNull RemoteSdkEditorContainer myParentContainer;
  private final Runnable myValidator;

  private final @NotNull BundleAccessor myBundleAccessor;
  private boolean myTempFilesPathVisible;

  private CredentialsType myConnectionType;
  private final Map<CredentialsType, TypeHandler> myCredentialsType2Handler;
  private final Set<CredentialsType> myUnsupportedConnectionTypes = new HashSet<>();

  private final @NotNull SdkScopeController mySdkScopeController;

  public CreateRemoteSdkForm(@Nullable Project project,
                             @NotNull RemoteSdkEditorContainer parentContainer,
                             @Nullable Runnable validator,
                             final @NotNull BundleAccessor bundleAccessor) {
    this(project, parentContainer, ApplicationOnlySdkScopeController.INSTANCE, validator, bundleAccessor);
  }

  public CreateRemoteSdkForm(@Nullable Project project,
                             @NotNull RemoteSdkEditorContainer parentContainer,
                             @NotNull SdkScopeController sdkScopeController,
                             @Nullable Runnable validator,
                             final @NotNull BundleAccessor bundleAccessor) {
    super(new BorderLayout());
    myProject = project;
    myParentContainer = parentContainer;
    Disposer.register(parentContainer.getDisposable(), this);
    mySdkScopeController = sdkScopeController;
    myBundleAccessor = bundleAccessor;
    myValidator = validator;
    add(myMainPanel, BorderLayout.CENTER);

    myStatusPanel = new StatusPanel();
    myStatusPanelHolder.setLayout(new BorderLayout());
    myStatusPanelHolder.add(myStatusPanel, BorderLayout.CENTER);

    setNameVisible(false);
    setTempFilesPathVisible(false);

    myInterpreterPathLabel.setLabelFor(myInterpreterPathField.getTextField());

    myInterpreterPathLabel.setText(myBundleAccessor.message("remote.interpreter.configure.path.label"));
    myHelpersPathLabel.setText(myBundleAccessor.message("remote.interpreter.configure.temp.files.path.label"));
    myInterpreterPathField.addActionListener(e -> {
      showBrowsePathsDialog(myInterpreterPathField, myBundleAccessor.message("remote.interpreter.configure.path.title"));
    });
    myHelpersPathField.addActionListener(e -> {
      showBrowsePathsDialog(myHelpersPathField, myBundleAccessor.message("remote.interpreter.configure.temp.files.path.title"));
    });

    myTypesPanel.setLayout(new ResizingCardLayout());

    myCredentialsType2Handler = new LinkedHashMap<>();

    installExtendedTypes(project);
    installRadioListeners(myCredentialsType2Handler.values());

    if (isSshSudoSupported()) {
      myRunAsRootViaSudoJBCheckBox.setText(
        bundleAccessor.message("remote.interpreter.configure.ssh.run_as_root_via_sudo.checkbox"));
      myRunAsRootViaSudoHelpPanel.add(ContextHelpLabel.create(
        bundleAccessor.message("remote.interpreter.configure.ssh.run_as_root_via_sudo.help")),
                                      BorderLayout.WEST);
    }
    else {
      myRunAsRootViaSudoPanel.setVisible(false);
    }

    TypeHandler handlerToSelect = getCredentialsTypeHandlerToSelect();
    if (handlerToSelect != null) {
      handlerToSelect.getRadioButton().setSelected(true);
    }

    radioSelected(true);
  }

  public void showBrowsePathsDialog(@NotNull TextFieldWithBrowseButton textFieldWithBrowseButton,
                                    @NlsContexts.DialogTitle @NotNull String dialogTitle) {
    if (myConnectionType instanceof PathsBrowserDialogProvider) {
      ((PathsBrowserDialogProvider)myConnectionType).showPathsBrowserDialog(
        myProject, textFieldWithBrowseButton.getTextField(),
        dialogTitle,
        () -> createSdkDataInner()
      );
    }
  }

  protected void disableChangeTypePanel() {
    myRadioPanel.setVisible(false);
  }

  private void installRadioListeners(final @NotNull Collection<? extends TypeHandler> values) {
    ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        radioSelected(true);
      }
    };
    for (TypeHandler typeHandler : values) {
      typeHandler.getRadioButton().addActionListener(l);
    }
  }

  private void installExtendedTypes(@Nullable Project project) {
    List<CredentialsType<?>> types = new ArrayList<>(CredentialsManager.getInstance().getAllTypes());
    types.sort(Comparator.comparing(CredentialsType::getWeight));
    for (CredentialsType<?> type : types) {
      CredentialsEditorProvider editorProvider = type instanceof CredentialsEditorProvider ? (CredentialsEditorProvider)type : null;
      if (editorProvider == null) {
        continue;
      }

      List<CredentialsLanguageContribution> contributions = getContributions();
      if (contributions.isEmpty()) {
        continue;
      }

      for (CredentialsLanguageContribution contribution : contributions) {
        if (contribution.getType() != type || !editorProvider.isAvailable(contribution)) {
          continue;
        }

        final CredentialsEditor<?> editor = editorProvider.createEditor(project, contribution, this);

        trackEditorLabelsColumn(editor);

        JBRadioButton typeButton = new JBRadioButton(editor.getName());
        myTypeButtonGroup.add(typeButton);
        myRadioPanel.add(typeButton);

        final JPanel editorMainPanel = editor.getMainPanel();
        myTypesPanel.add(editorMainPanel, type.getName());

        myCredentialsType2Handler.put(type,
                                      new TypeHandlerEx(typeButton,
                                                        editorMainPanel,
                                                        editorProvider.getDefaultInterpreterPath(myBundleAccessor),
                                                        type,
                                                        editor));
        // set initial connection type
        if (myConnectionType == null) {
          myConnectionType = type;
        }
      }
    }
  }

  protected @NotNull List<CredentialsLanguageContribution> getContributions() {
    return Collections.emptyList();
  }

  @Override
  public final @NotNull SdkScopeController getSdkScopeController() {
    return mySdkScopeController;
  }

  private void radioSelected(boolean propagateEvent) {
    CredentialsType selectedType = getSelectedType();

    CardLayout layout = (CardLayout)myTypesPanel.getLayout();
    layout.show(myTypesPanel, selectedType.getName());

    changeWindowHeightToPreferred();

    setBrowseButtonsVisible(myCredentialsType2Handler.get(selectedType).isBrowsingAvailable());

    if (propagateEvent) {
      myStatusPanel.resetState();

      // store interpreter path entered for previously selected type
      String interpreterPath = myInterpreterPathField.getText();
      if (StringUtil.isNotEmpty(interpreterPath)) {
        myCredentialsType2Handler.get(myConnectionType).setInterpreterPath(interpreterPath);
      }

      myConnectionType = selectedType;
      TypeHandler typeHandler = myCredentialsType2Handler.get(myConnectionType);

      myInterpreterPathField.setText(typeHandler.getInterpreterPath());

      typeHandler.onSelected();
    }
  }

  private void changeWindowHeightToPreferred() {
    final Window window = ComponentUtil.getWindow(myMainPanel);
    if (window != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Dimension currentSize = window.getSize();
        Dimension preferredSize = window.getPreferredSize();
        window.setSize(currentSize.width, preferredSize.height);
      }, ModalityState.stateForComponent(window));
    }
  }

  private CredentialsType getSelectedType() {
    for (Map.Entry<CredentialsType, TypeHandler> type2handler : myCredentialsType2Handler.entrySet()) {
      if (type2handler.getValue().getRadioButton().isSelected()) {
        return type2handler.getKey();
      }
    }
    throw new IllegalStateException();
  }

  private void setBrowseButtonsVisible(boolean visible) {
    myInterpreterPathField.getButton().setVisible(visible);
    myHelpersPathField.getButton().setVisible(visible);
  }

  // TODO: (next) may propose to start DockerMachine - somewhere

  public @Nullable JComponent getPreferredFocusedComponent() {
    if (myNameVisible) {
      return myNameField;
    }
    else {
      final CredentialsType selectedType = getSelectedType();
      final TypeHandler typeHandler = myCredentialsType2Handler.get(selectedType);
      if (typeHandler != null) {
        JComponent preferredFocusedComponent = UIUtil.getPreferredFocusedComponent(typeHandler.getContentComponent());
        if (preferredFocusedComponent != null) return preferredFocusedComponent;
      }
      return myTypesPanel;
    }
  }

  public T createSdkData() throws RemoteSdkException {
    return createSdkDataInner();
  }

  protected T createSdkDataInner() {
    final T sdkData = doCreateSdkData(getInterpreterPath());//todo ???
    //if (!myCredentialsType2Handler.containsKey(sdkData.getRemoteConnectionType())) return sdkData;


    //if () {
    //  ArrayUtil.mergeArrays(cases, exCases.toArray(new CredentialsCase[exCases.size()]))
    //}

    saveSelectedRadio(myConnectionType);
    myConnectionType.saveCredentials(
      sdkData,
      new CaseCollector() {

        @Override
        protected void processEx(CredentialsEditor editor, Object credentials) {
          editor.saveCredentials(credentials);
        }
      }.collectCases());

    sdkData.setRunAsRootViaSudo(myRunAsRootViaSudoJBCheckBox.isSelected());
    sdkData.setHelpersPath(getTempFilesPath());
    return sdkData;
  }

  protected abstract @NotNull T doCreateSdkData(@NotNull String interpreterPath);

  private void setNameVisible(boolean visible) {
    myNameField.setVisible(visible);
    myNameLabel.setVisible(visible);
    myNameVisible = visible;
  }

  public void setSdkName(String name) {
    if (name != null) {
      setNameVisible(true);
      myNameField.setText(name);
    }
  }


  public void init(final @NotNull T data) {
    myConnectionType = data.connectionCredentials().getRemoteConnectionType();

    TypeHandler typeHandler = myCredentialsType2Handler.get(myConnectionType);
    if (typeHandler == null) {
      typeHandler = new UnsupportedCredentialsTypeHandler(myConnectionType.getName());
      myUnsupportedConnectionTypes.add(myConnectionType);
      myCredentialsType2Handler.put(myConnectionType, typeHandler);
      myTypeButtonGroup.add(typeHandler.getRadioButton());
      myRadioPanel.add(typeHandler.getRadioButton());
      myTypesPanel.add(typeHandler.getContentComponent(), myConnectionType.getName());
    }

    typeHandler.getRadioButton().setSelected(true);

    boolean connectionTypeIsSupported = !myUnsupportedConnectionTypes.contains(myConnectionType);
    myRadioPanel.setVisible(connectionTypeIsSupported);

    data.switchOnConnectionType(
      new CaseCollector() {
        @Override
        protected void processEx(CredentialsEditor editor, Object credentials) {
          editor.init(credentials);
        }
      }.collectCases());
    radioSelected(false);
    String interpreterPath = data.getInterpreterPath();
    myInterpreterPathField.setText(interpreterPath);
    typeHandler.setInterpreterPath(interpreterPath);
    setTempFilesPath(data);

    if (isSshSudoSupported()) {
      myRunAsRootViaSudoJBCheckBox.setSelected(data.isRunAsRootViaSudo());
    }
  }

  private void setTempFilesPath(RemoteSdkAdditionalData data) {
    myHelpersPathField.setText(data.getHelpersPath());
    if (!StringUtil.isEmpty(data.getHelpersPath())) {
      setTempFilesPathVisible(true);
    }
  }

  protected void setTempFilesPathVisible(boolean visible) {
    myHelpersPathField.setVisible(visible);
    myHelpersPathLabel.setVisible(visible);
    myTempFilesPathVisible = visible;
  }

  protected void setInterpreterPathVisible(boolean visible) {
    myInterpreterPathField.setVisible(visible);
    myInterpreterPathLabel.setVisible(visible);
  }

  public String getInterpreterPath() {
    return myInterpreterPathField.getText().trim();
  }

  public String getTempFilesPath() {
    return myHelpersPathField.getText();
  }

  public @Nullable ValidationInfo validateRemoteInterpreter() {
    TypeHandler typeHandler = myCredentialsType2Handler.get(getSelectedType());
    if (StringUtil.isEmpty(getInterpreterPath())) {
      return new ValidationInfo(
        myBundleAccessor.message("remote.interpreter.unspecified.interpreter.path"),
        myInterpreterPathField);
    }
    if (myTempFilesPathVisible) {
      if (StringUtil.isEmpty(getTempFilesPath())) {
        return new ValidationInfo(myBundleAccessor.message("remote.interpreter.unspecified.temp.files.path"), myHelpersPathField);
      }
    }
    return typeHandler.validate();
  }

  public @Nullable String getSdkName() {
    if (myNameVisible) {
      return myNameField.getText().trim();
    }
    else {
      return null;
    }
  }

  public void updateModifiedValues(RemoteSdkProperties data) {
    myHelpersPathField.setText(data.getHelpersPath());
  }

  public void updateHelpersPath(@NlsSafe String helpersPath) {
    myHelpersPathField.setText(helpersPath);
  }

  @Override
  public boolean isSdkInConsistentState(@NotNull CredentialsType<?> connectionType) {
    return myCredentialsType2Handler.get(connectionType).getRadioButton().isSelected(); // TODO: may encapsutate
  }

  public @NlsContexts.DialogMessage String getValidationError() {
    return myStatusPanel.getError();
  }

  public @NlsContexts.DialogMessage @Nullable String validateFinal() {
    return myCredentialsType2Handler.get(myConnectionType).validateFinal();
  }

  @Override
  public void dispose() {
    // Disposable is the marker interface for CreateRemoteSdkForm
  }

  @Override
  public final @NotNull Disposable getDisposable() {
    return this;
  }

  @Override
  public final @NotNull BundleAccessor getBundleAccessor() {
    return myBundleAccessor;
  }

  private interface TypeHandler {
    @NotNull JPanel getContentComponent();

    @NotNull JBRadioButton getRadioButton();

    void onSelected();

    @NlsSafe @Nullable String getInterpreterPath();

    void setInterpreterPath(@Nullable String interpreterPath);

    @Nullable ValidationInfo validate();

    @NlsContexts.DialogMessage @Nullable String validateFinal();

    boolean isBrowsingAvailable();
  }

  private static final class UnsupportedCredentialsTypeHandler implements TypeHandler {
    private final @NotNull JBRadioButton myTypeButton;
    private final @NotNull JPanel myPanel;

    private UnsupportedCredentialsTypeHandler(@NlsContexts.RadioButton @Nullable String credentialsTypeName) {
      myTypeButton = new JBRadioButton(credentialsTypeName);
      myPanel = new JPanel(new BorderLayout());
      String errorMessage = ExecutionBundle.message("remote.interpreter.cannot.load.interpreter.message", credentialsTypeName);
      JBLabel errorLabel = new JBLabel(errorMessage);
      errorLabel.setIcon(AllIcons.General.BalloonError);
      myPanel.add(errorLabel, BorderLayout.CENTER);
    }

    @Override
    public @NotNull JPanel getContentComponent() {
      return myPanel;
    }

    @Override
    public @NotNull JBRadioButton getRadioButton() {
      return myTypeButton;
    }

    @Override
    public @Nullable String getInterpreterPath() {
      return null;
    }

    @Override
    public void setInterpreterPath(@Nullable String interpreterPath) {
    }

    @Override
    public void onSelected() {
    }

    @Override
    public @Nullable ValidationInfo validate() {
      return null;
    }

    @Override
    public @NlsContexts.DialogMessage @Nullable String validateFinal() {
      return null;
    }

    @Override
    public boolean isBrowsingAvailable() {
      return false;
    }
  }

  private final class TypeHandlerEx implements TypeHandler {

    private final @NotNull JBRadioButton myRadioButton;
    private final @NotNull JPanel myPanel;

    private @Nullable String myInterpreterPath;

    private final @NotNull CredentialsType<?> myType;
    private final @NotNull CredentialsEditor<?> myEditor;

    TypeHandlerEx(@NotNull JBRadioButton radioButton,
                  @NotNull JPanel panel,
                  @Nullable String defaultInterpreterPath,
                  @NotNull CredentialsType<?> type,
                  @NotNull CredentialsEditor editor) {
      myRadioButton = radioButton;
      myPanel = panel;
      myInterpreterPath = defaultInterpreterPath;
      myType = type;
      myEditor = editor;
    }

    public @NotNull CredentialsEditor getEditor() {
      return myEditor;
    }

    @Override
    public void onSelected() {
      myConnectionType = myType;
      myEditor.onSelected();
    }

    @Override
    public @NotNull JPanel getContentComponent() {
      return myPanel;
    }

    @Override
    public @NotNull JBRadioButton getRadioButton() {
      return myRadioButton;
    }

    @Override
    public void setInterpreterPath(@Nullable String interpreterPath) {
      myInterpreterPath = interpreterPath;
    }

    @Override
    public @Nullable String getInterpreterPath() {
      return myInterpreterPath;
    }

    @Override
    public @Nullable ValidationInfo validate() {
      return myEditor.validate();
    }

    @Override
    public @NlsContexts.DialogMessage @Nullable String validateFinal() {
      return myEditor.validateFinal(() -> createSdkDataInner(), helpersPath -> updateHelpersPath(helpersPath));
    }

    public @NotNull CredentialsType<?> getType() {
      return myType;
    }

    @Override
    public boolean isBrowsingAvailable() {
      return myType instanceof PathsBrowserDialogProvider;
    }
  }

  private abstract class CaseCollector {

    public CredentialsCase[] collectCases(CredentialsCase... cases) {
      List<CredentialsCase> exCases = new ArrayList<>();
      for (TypeHandler typeHandler : myCredentialsType2Handler.values()) {
        final TypeHandlerEx handlerEx = ObjectUtils.tryCast(typeHandler, TypeHandlerEx.class);
        if (handlerEx != null) {
          exCases.add(new CredentialsCase() {
            @Override
            public CredentialsType getType() {
              return handlerEx.getType();
            }

            @Override
            public void process(Object credentials) {
              processEx(handlerEx.getEditor(), credentials);
            }
          });
        }
      }

      return ArrayUtil.mergeArrays(cases, exCases.toArray(new CredentialsCase[0]));
    }

    protected abstract void processEx(CredentialsEditor editor, Object credentials);
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public final @NotNull RemoteSdkEditorContainer getParentContainer() {
    return myParentContainer;
  }

  @Override
  public @NotNull StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @Override
  public @Nullable Runnable getValidator() {
    return myValidator;
  }

  /**
   * Returns whether running SSH interpreter as root via sudo is
   * supported or not.
   */
  public boolean isSshSudoSupported() {
    return false;
  }

  /**
   * Returns whether editing of SDK with specified {@link CredentialsType} is
   * supported or not.
   * <p>
   * Certain remote interpreters (e.g. PHP, Python, Ruby, etc.) may or may not
   * support SDK with certain credentials type (e.g. Docker, Docker Compose,
   * WSL, etc.).
   *
   * @param type credentials type to check
   * @return whether editing of SDK is supported or not
   */
  public boolean isConnectionTypeSupported(@NotNull CredentialsType type) {
    return myCredentialsType2Handler.containsKey(type) && !myUnsupportedConnectionTypes.contains(type);
  }

  /**
   * {@link ResizingCardLayout#preferredLayoutSize(Container)} and {@link ResizingCardLayout#minimumLayoutSize(Container)} methods are the
   * same as in {@link CardLayout} but they take into account only visible components.
   */
  private static final class ResizingCardLayout extends CardLayout {
    @Override
    public Dimension preferredLayoutSize(Container parent) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        int ncomponents = parent.getComponentCount();
        int w = 0;
        int h = 0;

        for (int i = 0; i < ncomponents; i++) {
          Component comp = parent.getComponent(i);
          if (comp.isVisible()) {
            Dimension d = comp.getPreferredSize();
            if (d.width > w) {
              w = d.width;
            }
            if (d.height > h) {
              h = d.height;
            }
          }
        }
        return new Dimension(insets.left + insets.right + w + getHgap() * 2,
                             insets.top + insets.bottom + h + getVgap() * 2);
      }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      synchronized (parent.getTreeLock()) {
        Insets insets = parent.getInsets();
        int ncomponents = parent.getComponentCount();
        int w = 0;
        int h = 0;

        for (int i = 0; i < ncomponents; i++) {
          Component comp = parent.getComponent(i);
          if (comp.isVisible()) {
            Dimension d = comp.getMinimumSize();
            if (d.width > w) {
              w = d.width;
            }
            if (d.height > h) {
              h = d.height;
            }
          }
        }
        return new Dimension(insets.left + insets.right + w + getHgap() * 2,
                             insets.top + insets.bottom + h + getVgap() * 2);
      }
    }
  }

  @TestOnly
  public void selectType(CredentialsType credentialsType) {
    for (Map.Entry<CredentialsType, TypeHandler> type2handler : myCredentialsType2Handler.entrySet()) {
      if (type2handler.getKey() == credentialsType) {
        type2handler.getValue().getRadioButton().setSelected(true);
        break;
      }
    }
    radioSelected(true);
  }

  private void trackEditorLabelsColumn(@NotNull CredentialsEditor<?> editor) {
    if (editor instanceof FormWithAlignableLabelsColumn) {
      for (JBLabel label : ((FormWithAlignableLabelsColumn)editor).getLabelsColumn()) {
        label.addAncestorListener(myLabelsColumnTracker);
        label.addComponentListener(myLabelsColumnTracker);
      }
    }
  }

  private final @NotNull CredentialsEditorLabelsColumnTracker myLabelsColumnTracker = new CredentialsEditorLabelsColumnTracker();

  private final class CredentialsEditorLabelsColumnTracker implements ComponentListener, AncestorListener {
    private final @NotNull Set<JBLabel> myVisibleLabelsColumn = new HashSet<>();

    private @Nullable JBLabel myAnchoredLabel;

    @Override
    public void componentResized(ComponentEvent e) { /* do nothing */ }

    @Override
    public void componentMoved(ComponentEvent e) { /* do nothing */ }

    @Override
    public void componentShown(ComponentEvent e) { onEvent(e.getComponent()); }

    @Override
    public void componentHidden(ComponentEvent e) { onEvent(e.getComponent()); }

    @Override
    public void ancestorAdded(AncestorEvent event) { onEvent(event.getComponent()); }

    @Override
    public void ancestorRemoved(AncestorEvent event) { onEvent(event.getComponent()); }

    @Override
    public void ancestorMoved(AncestorEvent event) { onEvent(event.getComponent()); }

    private void onEvent(@Nullable Component component) {
      if (component == null) return;

      if (component instanceof JBLabel) {
        if (component.isShowing()) {
          onLabelShowing((JBLabel)component);
        }
        else {
          onLabelHidden((JBLabel)component);
        }
      }
    }

    private void onLabelShowing(@NotNull JBLabel component) {
      if (myVisibleLabelsColumn.add(component)) {
        alignForm();
      }
    }

    private void onLabelHidden(@NotNull JBLabel component) {
      if (myVisibleLabelsColumn.remove(component)) {
        alignForm();
      }
    }

    private void alignForm() {
      myInterpreterPathLabel.setAnchor(null);
      if (myAnchoredLabel != null) {
        myAnchoredLabel.setAnchor(null);
        myAnchoredLabel = null;
      }
      if (!myVisibleLabelsColumn.isEmpty()) {
        JBLabel labelWithMaxWidth = Collections.max(myVisibleLabelsColumn, Comparator.comparingInt(o -> o.getPreferredSize().width));
        if (myInterpreterPathLabel.getPreferredSize().width < labelWithMaxWidth.getPreferredSize().getWidth()) {
          myInterpreterPathLabel.setAnchor(labelWithMaxWidth);
        }
        else {
          for (JBLabel label : myVisibleLabelsColumn) {
            label.setAnchor(myInterpreterPathLabel);
            label.revalidate();
          }
          myAnchoredLabel = labelWithMaxWidth;
        }
      }
    }
  }

  @TestOnly
  public void setInterpreterPath(@NlsSafe @NotNull String interpreterPath) {
    myInterpreterPathField.setText(interpreterPath);
  }

  private void saveSelectedRadio(@NotNull CredentialsType<?> credentialsType) {
    getPropertiesComponent().setValue(getCredentialsTypePersistenceKey(), credentialsType.getName());
  }

  private @NotNull PropertiesComponent getPropertiesComponent() {
    return myProject == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(myProject);
  }

  private @Nullable TypeHandler getCredentialsTypeHandlerToSelect() {
    String credentialsTypeName = getPropertiesComponent().getValue(getCredentialsTypePersistenceKey());
    if (credentialsTypeName != null) {
      for (Map.Entry<CredentialsType, TypeHandler> entry : myCredentialsType2Handler.entrySet()) {
        if (entry.getKey().getName().equals(credentialsTypeName)) {
          return entry.getValue();
        }
      }
    }
    Iterator<TypeHandler> iterator = myCredentialsType2Handler.values().iterator();
    if (iterator.hasNext()) {
      return iterator.next();
    }
    return null;
  }

  private @NonNls @NotNull String getCredentialsTypePersistenceKey() {
    return "credentialsType " + getClass().getName();
  }
}
