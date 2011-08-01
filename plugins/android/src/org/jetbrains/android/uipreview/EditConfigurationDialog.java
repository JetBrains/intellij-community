package org.jetbrains.android.uipreview;

import com.android.ide.common.resources.configuration.*;
import com.android.resources.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("unchecked")
class EditConfigurationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.EditConfigurationDialog");

  private JPanel myDevicePanel;
  private JTextField myConfigNameField;
  private JBList myAvailableQualifiersList;
  private JButton myAddQualifierButton;
  private JButton myRemoveQualifierButton;
  private JPanel myQualifierOptionsPanel;
  private JPanel myContentPanel;
  private JBList myChosenQualifiersList;
  private JTextPane myConfigurationTextPane;
  private JPanel myLeftPanel;
  private JPanel myMiddlePanel;
  private JPanel myRightPanel;
  private JPanel myListsPanel;

  private final EditDeviceForm myEditDeviceForm = new EditDeviceForm();
  private final Map<String, MyQualifierEditor> myEditors = new HashMap<String, MyQualifierEditor>();

  private final FolderConfiguration myAvailableQualifiersConfig = new FolderConfiguration();
  private final FolderConfiguration myChosenQualifiersConfig = new FolderConfiguration();
  private FolderConfiguration myActualQualifiersConfig = new FolderConfiguration();

  private final DocumentListener myUpdatingDocumentListener = new DocumentAdapter() {
    @Override
    protected void textChanged(DocumentEvent e) {
      applyAndUpdateConfigLabel();
    }
  };

  public EditConfigurationDialog(@NotNull Project project, @Nullable Object deviceOfConfig) {
    super(project);

    myConfigurationTextPane.setOpaque(false);

    LayoutDevice device = null;
    FolderConfiguration config = null;

    if (deviceOfConfig instanceof LayoutDevice) {
      device = (LayoutDevice)deviceOfConfig;
    }
    else if (deviceOfConfig instanceof LayoutDeviceConfiguration) {
      final LayoutDeviceConfiguration deviceConfig = (LayoutDeviceConfiguration)deviceOfConfig;
      device = deviceConfig.getDevice();
      config = deviceConfig.getConfiguration();
      myConfigNameField.setText(deviceConfig.getName());
    }

    myDevicePanel.add(myEditDeviceForm.getContentPanel());

    myAvailableQualifiersConfig.createDefault();

    myAvailableQualifiersConfig.setLanguageQualifier(null);
    myAvailableQualifiersConfig.setVersionQualifier(null);
    myAvailableQualifiersConfig.setNightModeQualifier(null);
    myAvailableQualifiersConfig.setDockModeQualifier(null);
    myAvailableQualifiersConfig.setRegionQualifier(null);

    myChosenQualifiersConfig.reset();

    for (ResourceQualifier qualifier : myAvailableQualifiersConfig.getQualifiers()) {
      final String name = qualifier.getShortName();
      if (qualifier instanceof CountryCodeQualifier) {
        myEditors.put(name, new MyCountryCodeEditor());
      }
      else if (qualifier instanceof NetworkCodeQualifier) {
        myEditors.put(name, new MyNetworkCodeEditor());
      }
      else if (qualifier instanceof KeyboardStateQualifier) {
        myEditors.put(name, new MyKeyboardStateEditor());
      }
      else if (qualifier instanceof NavigationMethodQualifier) {
        myEditors.put(name, new MyNavigationMethodEditor());
      }
      else if (qualifier instanceof NavigationStateQualifier) {
        myEditors.put(name, new MyNavigationStateEditor());
      }
      else if (qualifier instanceof PixelDensityQualifier) {
        myEditors.put(name, new MyDensityEditor());
      }
      else if (qualifier instanceof ScreenDimensionQualifier) {
        myEditors.put(name, new MyScreenDimensionEditor());
      }
      else if (qualifier instanceof ScreenOrientationQualifier) {
        myEditors.put(name, new MyScreenOrientationEditor());
      }
      else if (qualifier instanceof ScreenRatioQualifier) {
        myEditors.put(name, new MyScreenRatioEditor());
      }
      else if (qualifier instanceof ScreenSizeQualifier) {
        myEditors.put(name, new MyScreenSizeEditor());
      }
      else if (qualifier instanceof TextInputMethodQualifier) {
        myEditors.put(name, new MyTextInputMethodEditor());
      }
      else if (qualifier instanceof TouchScreenQualifier) {
        myEditors.put(name, new MyTouchScreenEditor());
      }
    }

    for (String name : myEditors.keySet()) {
      final MyQualifierEditor editor = myEditors.get(name);
      myQualifierOptionsPanel.add(editor.getComponent(), name);
    }

    if (config != null) {
      myChosenQualifiersConfig.set(config);
      myAvailableQualifiersConfig.substract(config);

      for (ResourceQualifier qualifier : config.getQualifiers()) {
        final MyQualifierEditor editor = myEditors.get(qualifier.getShortName());
        if (editor != null) {
          editor.reset(qualifier);
        }
      }
    }

    if (device != null) {
      myEditDeviceForm.reset(device);
    }

    myAvailableQualifiersList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof ResourceQualifier) {
          value = ((ResourceQualifier)value).getShortName();
        }
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });
    myChosenQualifiersList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof ResourceQualifier) {
          final ResourceQualifier qualifier = getActualQualifier((ResourceQualifier)value);
          final String shortDisplayValue = qualifier.getShortDisplayValue();

          value = shortDisplayValue != null && shortDisplayValue.length() > 0
                  ? shortDisplayValue
                  : qualifier.getShortName() + " (?)";
        }
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    myAddQualifierButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ResourceQualifier selectedQualifier = (ResourceQualifier)myAvailableQualifiersList.getSelectedValue();
        if (selectedQualifier != null) {
          final int index = myAvailableQualifiersList.getSelectedIndex();

          myAvailableQualifiersConfig.removeQualifier(selectedQualifier);
          myChosenQualifiersConfig.addQualifier(selectedQualifier);
          updateLists();
          applyAndUpdateConfigLabel();

          if (index >= 0) {
            myAvailableQualifiersList.setSelectedIndex(Math.min(index, myAvailableQualifiersList.getItemsCount() - 1));
          }
        }
      }
    });

    myRemoveQualifierButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ResourceQualifier selectedQualifier = (ResourceQualifier)myChosenQualifiersList.getSelectedValue();
        if (selectedQualifier != null) {
          final int index = myChosenQualifiersList.getSelectedIndex();

          myChosenQualifiersConfig.removeQualifier(selectedQualifier);
          myAvailableQualifiersConfig.addQualifier(selectedQualifier);
          updateLists();
          applyAndUpdateConfigLabel();

          if (index >= 0) {
            myChosenQualifiersList.setSelectedIndex(Math.min(index, myChosenQualifiersList.getItemsCount() - 1));
          }
        }
      }
    });

    myAvailableQualifiersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    myChosenQualifiersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
        updateQualifierEditor();
      }
    });


    myEditDeviceForm.getNameField().getDocument().addDocumentListener(myUpdatingDocumentListener);
    myConfigNameField.getDocument().addDocumentListener(myUpdatingDocumentListener);

    updateLists();
    updateButtons();
    updateQualifierEditor();
    applyAndUpdateConfigLabel();

    init();
  }

  private ResourceQualifier getActualQualifier(ResourceQualifier qualifier) {
    for (ResourceQualifier qualifier1 : myActualQualifiersConfig.getQualifiers()) {
      if (Comparing.equal(qualifier1.getShortName(), qualifier.getShortName())) {
        return qualifier1;
      }
    }
    return qualifier;
  }

  private void updateQualifierEditor() {
    final ResourceQualifier selectedQualifier = (ResourceQualifier)myChosenQualifiersList.getSelectedValue();
    if (selectedQualifier != null && myEditors.containsKey(selectedQualifier.getShortName())) {
      final CardLayout layout = (CardLayout)myQualifierOptionsPanel.getLayout();
      layout.show(myQualifierOptionsPanel, selectedQualifier.getShortName());
      boolean visible = myQualifierOptionsPanel.isVisible();
      myQualifierOptionsPanel.setVisible(true);
      if (!visible) {
        myContentPanel.revalidate();
      }
    }
    else {
      myQualifierOptionsPanel.setVisible(false);
    }
  }

  private void updateButtons() {
    myAddQualifierButton.setEnabled(myAvailableQualifiersList.getSelectedIndex() >= 0);
    myRemoveQualifierButton.setEnabled(myChosenQualifiersList.getSelectedIndex() >= 0);
  }

  private void updateLists() {
    Object qualifier = myAvailableQualifiersList.getSelectedValue();
    myAvailableQualifiersList.setModel(new CollectionListModel(myAvailableQualifiersConfig.getQualifiers()));
    myAvailableQualifiersList.setSelectedValue(qualifier, true);

    if (myAvailableQualifiersList.getSelectedValue() == null && myAvailableQualifiersList.getItemsCount() > 0) {
      myAvailableQualifiersList.setSelectedIndex(0);
    }

    qualifier = myChosenQualifiersList.getSelectedValue();
    myChosenQualifiersList.setModel(new CollectionListModel(myChosenQualifiersConfig.getQualifiers()));
    myChosenQualifiersList.setSelectedValue(qualifier, true);

    if (myChosenQualifiersList.getSelectedValue() == null && myChosenQualifiersList.getItemsCount() > 0) {
      myChosenQualifiersList.setSelectedIndex(0);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public EditDeviceForm getEditDeviceForm() {
    return myEditDeviceForm;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    applyAndUpdateConfigLabel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myEditDeviceForm.getNameField().getText().length() == 0) {
      return myEditDeviceForm.getNameField();
    }
    return myConfigNameField;
  }

  private void applyAndUpdateConfigLabel() {
    try {
      if (myEditDeviceForm.getName().length() == 0) {
        throw new InvalidOptionValueException("specify device name");
      }

      if (myConfigNameField.getText().length() == 0) {
        throw new InvalidOptionValueException("specify configuration name");
      }

      final FolderConfiguration newConfig = new FolderConfiguration();

      for (ResourceQualifier qualifier : myChosenQualifiersConfig.getQualifiers()) {
        final MyQualifierEditor editor = myEditors.get(qualifier.getShortName());
        if (editor != null) {
          newConfig.addQualifier(editor.apply());
        }
      }

      myActualQualifiersConfig = newConfig;
    }
    catch (InvalidOptionValueException e) {
      LOG.debug(e);
      myConfigurationTextPane.setText("Error: " + e.getMessage());
      setOKActionEnabled(false);
      return;
    }
    finally {
      myAvailableQualifiersList.repaint();
      myChosenQualifiersList.repaint();
    }
    myConfigurationTextPane.setText("Configuration: " + myActualQualifiersConfig.toDisplayString());
    setOKActionEnabled(true);
  }

  @NotNull
  public String getConfigName() {
    return myConfigNameField.getText();
  }

  @NotNull
  public FolderConfiguration getConfiguration() {
    return myActualQualifiersConfig;
  }

  private void createUIComponents() {
    myLeftPanel = new JPanel(new BorderLayout(5, 5));
    myAvailableQualifiersList = new JBList();
    myAvailableQualifiersList.setMinimumSize(new Dimension(10, 10));
    myLeftPanel
      .add(new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.available.qualifiers.label")), BorderLayout.NORTH);
    myLeftPanel.add(new JBScrollPane(myAvailableQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    myRightPanel = new JPanel(new BorderLayout(5, 5));
    myChosenQualifiersList = new JBList();
    myChosenQualifiersList.setMinimumSize(new Dimension(10, 10));
    myRightPanel
      .add(new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.choosen.qualifiers.label")), BorderLayout.NORTH);
    myRightPanel.add(new JBScrollPane(myChosenQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(FlowLayout.CENTER, 0, 0, true, false));
    myAddQualifierButton = new JButton(">>");
    buttonsPanel.add(myAddQualifierButton);
    myRemoveQualifierButton = new JButton("<<");
    buttonsPanel.add(myRemoveQualifierButton);
    myMiddlePanel = buttonsPanel;

    final int gap = 5;

    myListsPanel = new JPanel(new AbstractLayoutManager() {
      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
          final Dimension leftPref = myLeftPanel.getPreferredSize();
          final Dimension rightPref = myRightPanel.getPreferredSize();
          final Dimension middlePref = myMiddlePanel.getPreferredSize();
          final Insets insets = target.getInsets();

          final int width = leftPref.width + middlePref.width + rightPref.width + insets.left + insets.right + gap * 2;
          final int height = Math
                               .max(leftPref.height, Math.max(rightPref.height, middlePref.height)) + insets.top + insets.bottom;
          return new Dimension(width, height);
        }
      }

      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
          final Insets insets = target.getInsets();
          int top = insets.top;
          int bottom = target.getHeight() - insets.bottom;
          int left = insets.left;
          int right = target.getWidth() - insets.right;

          final int middleWidth = myMiddlePanel.getPreferredSize().width + gap * 2;
          final int listWidth = (right - left - middleWidth) / 2;
          final int height = bottom - top;

          myLeftPanel.setSize(listWidth, height);
          myRightPanel.setSize(listWidth, height);
          myMiddlePanel.setSize(middleWidth, height);

          myLeftPanel.setBounds(left, top, listWidth, height);
          myRightPanel.setBounds(right - listWidth, top, listWidth, height);
          myMiddlePanel.setBounds(left + listWidth + gap, top, middleWidth - gap * 2, height);
        }
      }
    });

    myListsPanel.add(myLeftPanel);
    myListsPanel.add(myMiddlePanel);
    myListsPanel.add(myRightPanel);
  }

  private abstract static class MyQualifierEditor<T extends ResourceQualifier> {
    abstract JComponent getComponent();

    abstract void reset(@NotNull T qualifier);

    @NotNull
    abstract T apply() throws InvalidOptionValueException;
  }

  private class MyCountryCodeEditor extends MyQualifierEditor<CountryCodeQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile country code<br>(3 digits):</body></html>");
      label.setLabelFor(myTextField);
      myTextField.setColumns(3);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(label);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull CountryCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    CountryCodeQualifier apply() throws InvalidOptionValueException {
      if (myTextField.getText().length() != 3) {
        throw new InvalidOptionValueException("Country code must contain 3 digits");
      }
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code < 100 || code > 999) {
          throw new InvalidOptionValueException("Incorrect country code");
        }
        return new CountryCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Country code must be a number");
      }
    }
  }

  private class MyNetworkCodeEditor extends MyQualifierEditor<NetworkCodeQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile network code<br>(1-3 digits):</body></html>");
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull NetworkCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    NetworkCodeQualifier apply() throws InvalidOptionValueException {
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code <= 0 || code >= 1000) {
          throw new InvalidOptionValueException("Incorrect network code");
        }
        return new NetworkCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Network code must be a number");
      }
    }
  }

  private abstract class MyEnumBasedEditor<T extends ResourceQualifier, U extends Enum<U>> extends MyQualifierEditor<T> {
    private final JComboBox myComboBox = new JComboBox();
    private final Class<U> myEnumClass;

    protected MyEnumBasedEditor(@NotNull Class<U> enumClass) {
      myEnumClass = enumClass;
    }

    @Override
    JComponent getComponent() {
      myComboBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          applyAndUpdateConfigLabel();
        }
      });

      myComboBox.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          if (value instanceof ResourceEnum) {
            value = ((ResourceEnum)value).getShortDisplayValue();
          }
          return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
      });

      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel(getCaption());
      label.setLabelFor(myComboBox);
      myComboBox.setModel(new EnumComboBoxModel<U>(myEnumClass));
      panel.add(label);
      panel.add(myComboBox);
      return panel;
    }

    @NotNull
    protected abstract String getCaption();

    @Override
    void reset(@NotNull T qualifier) {
      final U value = getValue(qualifier);
      if (value != null) {
        myComboBox.setSelectedItem(value);
      }
      else if (myComboBox.getItemCount() > 0) {
        myComboBox.setSelectedIndex(0);
      }
    }

    protected abstract U getValue(@NotNull T qualifier);

    @NotNull
    protected abstract T getQualifier(@NotNull U value);

    @NotNull
    protected abstract String getErrorMessage();

    @NotNull
    @Override
    T apply() throws InvalidOptionValueException {
      final U selectedItem = (U)myComboBox.getSelectedItem();
      if (selectedItem == null) {
        throw new InvalidOptionValueException(getErrorMessage());
      }
      return getQualifier(selectedItem);
    }
  }

  private class MyScreenSizeEditor extends MyEnumBasedEditor<ScreenSizeQualifier, ScreenSize> {
    private MyScreenSizeEditor() {
      super(ScreenSize.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen size:";
    }

    @NotNull
    @Override
    protected ScreenSize getValue(@NotNull ScreenSizeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenSizeQualifier getQualifier(@NotNull ScreenSize value) {
      return new ScreenSizeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen size";
    }
  }

  private class MyScreenOrientationEditor extends MyEnumBasedEditor<ScreenOrientationQualifier, ScreenOrientation> {
    private MyScreenOrientationEditor() {
      super(ScreenOrientation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen orientation:";
    }

    @NotNull
    @Override
    protected ScreenOrientation getValue(@NotNull ScreenOrientationQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenOrientationQualifier getQualifier(@NotNull ScreenOrientation value) {
      return new ScreenOrientationQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen orientation";
    }
  }

  private class MyScreenRatioEditor extends MyEnumBasedEditor<ScreenRatioQualifier, ScreenRatio> {
    private MyScreenRatioEditor() {
      super(ScreenRatio.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen ratio:";
    }

    @NotNull
    @Override
    protected ScreenRatio getValue(@NotNull ScreenRatioQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenRatioQualifier getQualifier(@NotNull ScreenRatio value) {
      return new ScreenRatioQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen ratio";
    }
  }

  private class MyDensityEditor extends MyEnumBasedEditor<PixelDensityQualifier, Density> {
    private MyDensityEditor() {
      super(Density.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Density:";
    }

    @NotNull
    @Override
    protected Density getValue(@NotNull PixelDensityQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected PixelDensityQualifier getQualifier(@NotNull Density value) {
      return new PixelDensityQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify density";
    }
  }

  private class MyTouchScreenEditor extends MyEnumBasedEditor<TouchScreenQualifier, TouchScreen> {
    private MyTouchScreenEditor() {
      super(TouchScreen.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Touch screen:";
    }

    @NotNull
    @Override
    protected TouchScreen getValue(@NotNull TouchScreenQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TouchScreenQualifier getQualifier(@NotNull TouchScreen value) {
      return new TouchScreenQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify touch screen";
    }
  }

  private class MyKeyboardStateEditor extends MyEnumBasedEditor<KeyboardStateQualifier, KeyboardState> {
    private MyKeyboardStateEditor() {
      super(KeyboardState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Keyboard state:";
    }

    @NotNull
    @Override
    protected KeyboardState getValue(@NotNull KeyboardStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected KeyboardStateQualifier getQualifier(@NotNull KeyboardState value) {
      return new KeyboardStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify keyboard state";
    }
  }

  private class MyTextInputMethodEditor extends MyEnumBasedEditor<TextInputMethodQualifier, Keyboard> {
    private MyTextInputMethodEditor() {
      super(Keyboard.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Text input method:";
    }

    @NotNull
    @Override
    protected Keyboard getValue(@NotNull TextInputMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TextInputMethodQualifier getQualifier(@NotNull Keyboard value) {
      return new TextInputMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify text input method";
    }
  }

  private class MyNavigationStateEditor extends MyEnumBasedEditor<NavigationStateQualifier, NavigationState> {

    private MyNavigationStateEditor() {
      super(NavigationState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation state:";
    }

    @NotNull
    @Override
    protected NavigationState getValue(@NotNull NavigationStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationStateQualifier getQualifier(@NotNull NavigationState value) {
      return new NavigationStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation state";
    }
  }

  private class MyNavigationMethodEditor extends MyEnumBasedEditor<NavigationMethodQualifier, Navigation> {
    private MyNavigationMethodEditor() {
      super(Navigation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation method:";
    }

    @NotNull
    @Override
    protected Navigation getValue(@NotNull NavigationMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationMethodQualifier getQualifier(@NotNull Navigation value) {
      return new NavigationMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation method";
    }
  }

  private class MyScreenDimensionEditor extends MyQualifierEditor<ScreenDimensionQualifier> {
    private final JTextField mySizeField1 = new JTextField();
    private final JTextField mySizeField2 = new JTextField();

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("Screen dimension:");
      label.setLabelFor(mySizeField1);
      panel.add(label);
      panel.add(mySizeField1);
      panel.add(mySizeField2);
      mySizeField1.getDocument().addDocumentListener(myUpdatingDocumentListener);
      mySizeField2.getDocument().addDocumentListener(myUpdatingDocumentListener);
      return panel;
    }

    @Override
    void reset(@NotNull ScreenDimensionQualifier qualifier) {
      final int value1 = qualifier.getValue1();
      if (value1 >= 0) {
        mySizeField1.setText(Integer.toString(value1));
      }

      final int value2 = qualifier.getValue2();
      if (value2 >= 0) {
        mySizeField2.setText(Integer.toString(value2));
      }
    }

    @NotNull
    @Override
    ScreenDimensionQualifier apply() throws InvalidOptionValueException {
      try {
        final int size1 = Integer.parseInt(mySizeField1.getText());
        final int size2 = Integer.parseInt(mySizeField2.getText());

        if (size1 <= 0 || size2 <= 0) {
          throw new InvalidOptionValueException("Incorrect screen dimension");
        }
        return new ScreenDimensionQualifier(size1, size2);
      }
      catch (NumberFormatException e) {
        LOG.debug(e);
        throw new InvalidOptionValueException("Incorrect screen dimension");
      }
    }
  }

  private static class InvalidOptionValueException extends Exception {
    public InvalidOptionValueException(String message) {
      super(message);
    }
  }
}

