// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.config.AbstractProperty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Dimension;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

public class AntSetPanel {
  private final Form myForm;
  private final GlobalAntConfiguration myAntConfiguration;

  public AntSetPanel() {
    this(GlobalAntConfiguration.getInstance());
  }

  AntSetPanel(GlobalAntConfiguration antConfiguration) {
    myAntConfiguration = antConfiguration;
    myForm = new Form(antConfiguration);
  }

  public @Nullable AntInstallation showDialog(JComponent parent) {
    final DialogWrapper dialog = new MyDialog(parent);
    if (!dialog.showAndGet()) {
      return null;
    }

    apply();
    return myForm.getSelectedAnt();
  }

  void reset() {
    myForm.setAnts(myAntConfiguration.getConfiguredAnts().values());
  }

  void apply() {
    for (AntInstallation ant : myForm.getRemovedAnts()) {
      myAntConfiguration.removeConfiguration(ant);
    }

    final Map<AntReference, AntInstallation> currentAnts = myAntConfiguration.getConfiguredAnts();
    for (AntInstallation installation : currentAnts.values()) {
      installation.updateClasspath();
    }

    for (AntInstallation ant : myForm.getAddedAnts()) {
      myAntConfiguration.addConfiguration(ant);
    }
    myForm.applyModifications();
  }

  public void setSelection(AntInstallation antInstallation) {
    myForm.selectAnt(antInstallation);
  }

  public JComponent getComponent() {
    return myForm.getComponent();
  }

  private static class Form implements AntUIUtil.PropertiesEditor<AntInstallation> {
    private final Splitter mySplitter = new JBSplitter("antConfigurations.splitter", 0.3f);
    private final RightPanel myRightPanel;
    private final AnActionListEditor<AntInstallation> myAnts = new AnActionListEditor<>();
    private final UIPropertyBinding.Composite myBinding = new UIPropertyBinding.Composite();
    private final EditPropertyContainer myGlobalWorkingProperties;
    private final Map<AntInstallation, EditPropertyContainer> myWorkingProperties = new HashMap<>();

    private AntInstallation myCurrent;
    private final PropertyChangeListener myImmediateUpdater = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        myBinding.apply(getProperties(myCurrent));
        myAnts.updateItem(myCurrent);
      }
    };

    Form(final GlobalAntConfiguration antInstallation) {
      mySplitter.setShowDividerControls(true);
      mySplitter.setFirstComponent(myAnts);
      myGlobalWorkingProperties = new EditPropertyContainer(antInstallation.getProperties());
      myRightPanel = new RightPanel(myBinding, myImmediateUpdater);
      mySplitter.setSecondComponent(myRightPanel.myWholePanel);
      myAnts.addAddAction(new NewAntFactory(myAnts));
      myAnts.addRemoveButtonForAnt(antInstallation.IS_USER_ANT, AntBundle.message("remove.action.name"));
      myAnts.actionsBuilt();
      JList<AntInstallation> list = myAnts.getList();
      list.setCellRenderer(new AntUIUtil.AntInstallationRenderer(this));
      list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          if (myCurrent != null) myBinding.apply(getProperties(myCurrent));
          myCurrent = myAnts.getSelectedItem();
          if (myCurrent == null) {
            myBinding.loadValues(AbstractProperty.AbstractPropertyContainer.EMPTY);
            myBinding.beDisabled();
          }
          else {
            if (antInstallation.IS_USER_ANT.value(myCurrent)) {
              myBinding.beEnabled();
            }
            else {
              myBinding.beDisabled();
            }
            myBinding.loadValues(getProperties(myCurrent));
          }
        }
      });
    }

    public JList<AntInstallation> getAntsList() {
      return myAnts.getList();
    }

    public JComponent getComponent() {
      return mySplitter;
    }

    public AntInstallation getSelectedAnt() {
      return myAnts.getSelectedItem();
    }

    public void setAnts(Collection<AntInstallation> antInstallations) {
      myAnts.setItems(antInstallations);
    }

    public void applyModifications() {
      if (myCurrent != null) myBinding.apply(getProperties(myCurrent));
      ArrayList<AbstractProperty> properties = new ArrayList<>();
      myBinding.addAllPropertiesTo(properties);
      for (AntInstallation ant : myWorkingProperties.keySet()) {
        EditPropertyContainer container = myWorkingProperties.get(ant);
        container.apply();
      }
      myGlobalWorkingProperties.apply();
    }

    public void selectAnt(AntInstallation antInstallation) {
      myAnts.setSelection(antInstallation);
    }

    public ArrayList<AntInstallation> getAddedAnts() {
      return myAnts.getAdded();
    }

    public ArrayList<AntInstallation> getRemovedAnts() {
      return myAnts.getRemoved();
    }

    @Override
    public EditPropertyContainer getProperties(AntInstallation ant) {
      EditPropertyContainer properties = myWorkingProperties.get(ant);
      if (properties != null) return properties;
      properties = new EditPropertyContainer(myGlobalWorkingProperties, ant.getProperties());

      myWorkingProperties.put(ant, properties);
      return properties;
    }

    private static class RightPanel {
      private final JLabel myNameLabel;
      private final JLabel myHome;
      private final JTextField myName;
      private final AntClasspathEditorPanel myClasspath;
      private final JPanel myWholePanel;

      RightPanel(UIPropertyBinding.Composite binding, PropertyChangeListener immediateUpdater) {
        {
          // GUI initializer generated by IntelliJ IDEA GUI Designer
          // >>> IMPORTANT!! <<<
          // DO NOT EDIT OR ADD ANY CODE HERE!
          myWholePanel = new JPanel();
          myWholePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
          myNameLabel = new JLabel();
          this.$$$loadLabelText$$$(myNameLabel, this.$$$getMessageFromBundle$$$("messages/AntBundle", "ant.settings.name.label"));
          myWholePanel.add(myNameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
          final JLabel label1 = new JLabel();
          this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/AntBundle", "ant.settings.home.label"));
          myWholePanel.add(label1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0,
                                                       false));
          myClasspath = new AntClasspathEditorPanel();
          myClasspath.setEnabled(true);
          myWholePanel.add(myClasspath, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            null,
                                                            null, null, 0, false));
          final JPanel panel1 = new JPanel();
          panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
          myWholePanel.add(panel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null,
                                                       null, 0, false));
          myName = new JTextField();
          panel1.add(myName, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                 new Dimension(150, -1), null, 0, false));
          myHome = new JLabel();
          myHome.setText("");
          myWholePanel.add(myHome, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0,
                                                       false));
        }
        myNameLabel.setLabelFor(myName);
        binding.addBinding(myClasspath.setClasspathProperty(AntInstallation.CLASS_PATH));
        binding.bindString(myHome, AntInstallation.HOME_DIR);
        binding.bindString(myName, AntInstallation.NAME).addChangeListener(immediateUpdater);
      }

      private static Method $$$cachedGetBundleMethod$$$ = null;

      /** @noinspection ALL */
      private String $$$getMessageFromBundle$$$(String path, String key) {
        ResourceBundle bundle;
        try {
          Class<?> thisClass = this.getClass();
          if ($$$cachedGetBundleMethod$$$ == null) {
            Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
            $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
          }
          bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        }
        catch (Exception e) {
          bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
      }

      /** @noinspection ALL */
      private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
          if (text.charAt(i) == '&') {
            i++;
            if (i == text.length()) break;
            if (!haveMnemonic && text.charAt(i) != '&') {
              haveMnemonic = true;
              mnemonic = text.charAt(i);
              mnemonicIndex = result.length();
            }
          }
          result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
          component.setDisplayedMnemonic(mnemonic);
          component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
      }

      /** @noinspection ALL */
      public JComponent $$$getRootComponent$$$() { return myWholePanel; }
    }
  }

  private static class NewAntFactory implements Factory<AntInstallation> {
    private final AnActionListEditor<AntInstallation> myParent;

    NewAntFactory(AnActionListEditor<AntInstallation> parent) {
      myParent = parent;
    }

    @Override
    public AntInstallation create() {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      VirtualFile file = FileChooser.chooseFile(descriptor, myParent, null, null);
      if (file == null) return null;
      try {
        final AntInstallation inst = AntInstallation.fromHome(file.getPresentableUrl());
        adjustName(inst);
        return inst;
      }
      catch (AntInstallation.ConfigurationException e) {
        Messages.showErrorDialog(myParent, e.getMessage(), AntBundle.message("ant.setup.dialog.title"));
        return null;
      }
    }

    private void adjustName(final AntInstallation justCreated) {
      int nameIndex = 0;
      String adjustedName = justCreated.getName();
      final ListModel<AntInstallation> model = myParent.getList().getModel();

      int idx = 0;
      while (idx < model.getSize()) {
        final AntInstallation inst = model.getElementAt(idx++);
        if (adjustedName.equals(inst.getName())) {
          adjustedName = justCreated.getName() + " (" + (++nameIndex) + ")";
          idx = 0; // search from beginning
        }
      }

      if (!adjustedName.equals(justCreated.getName())) {
        justCreated.setName(adjustedName);
      }
    }
  }

  private class MyDialog extends DialogWrapper {
    MyDialog(final JComponent parent) {
      super(parent, true);
      setTitle(AntBundle.message("configure.ant.dialog.title"));
      init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myForm.getComponent();
    }

    @Override
    protected @NonNls String getDimensionServiceKey() {
      return "antSetDialogDimensionKey";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myForm.getAntsList();
    }

    @Override
    protected void doOKAction() {
      final Set<String> names = new HashSet<>();
      final ListModel<AntInstallation> model = myForm.getAntsList().getModel();
      for (int idx = 0; idx  < model.getSize(); idx++) {
        final AntInstallation inst = model.getElementAt(idx);
        final @NlsSafe String name = AntInstallation.NAME.get(myForm.getProperties(inst));
        if (names.contains(name)) {
          Messages.showErrorDialog(AntBundle.message("dialog.message.duplicate.ant.installation.name", name), getTitle());
          return;
        }
        names.add(name);
      }

      super.doOKAction();
    }
  }
}
