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
package com.intellij.cvsSupport2.ui.experts.checkout;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsFile;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnEnvironment;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.cvsSupport2.ui.ChangeKeywordSubstitutionPanel;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.checkout.CheckoutStrategy;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * author: lesya
 */
public class ChooseCheckoutMode extends WizardStep {

  private File mySelectedLocation;
  private final Collection<File> myCvsPaths = new ArrayList<>();
  private final DefaultListModel myCheckoutModeModel = new DefaultListModel();
  private final JList myCheckoutModeList = new JBList(myCheckoutModeModel);
  private final JCheckBox myMakeNewFilesReadOnly = new JCheckBox(CvsBundle.message("checkbox.make.new.files.read.only"));
  private final JCheckBox myPruneEmptyDirectories = new JCheckBox(CvsBundle.message("checkbox.prune.empty.directories"));
  private final ChangeKeywordSubstitutionPanel myChangeKeywordSubstitutionPanel;
  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  private final JPanel myCenterPanel = new JPanel(new CardLayout());

  @NonNls public static final String LIST = "LIST";
  @NonNls public static final String MESSAGE = "MESSSAGE";
  @NonNls private final JLabel myMessage = new JLabel("XXX");

  private CvsRootConfiguration myConfiguration = null;

  public ChooseCheckoutMode(Project project, CheckoutWizard wizard) {
    super("###", wizard);
    myCheckoutModeList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        final CheckoutStrategy checkoutStrategy = (CheckoutStrategy)value;
        append(checkoutStrategy.getResult().getAbsolutePath(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
        setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
      }
    });
    myCheckoutModeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        getWizard().updateButtons();
      }
    });

    myCheckoutModeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final CvsApplicationLevelConfiguration config = CvsApplicationLevelConfiguration.getInstance();
    myMakeNewFilesReadOnly.setSelected(config.MAKE_CHECKED_OUT_FILES_READONLY);
    myPruneEmptyDirectories.setSelected(config.CHECKOUT_PRUNE_EMPTY_DIRECTORIES);
    myChangeKeywordSubstitutionPanel =
      new ChangeKeywordSubstitutionPanel(KeywordSubstitution.getValue(config.CHECKOUT_KEYWORD_SUBSTITUTION));

    myCenterPanel.add(LIST, ScrollPaneFactory.createScrollPane(myCheckoutModeList));
    final JPanel messagePanel = new JPanel(new BorderLayout(2,4));
    messagePanel.add(myMessage, BorderLayout.NORTH);
    messagePanel.setBackground(UIUtil.getTableBackground());
    myMessage.setBackground(UIUtil.getTableBackground());
    myCenterPanel.add(MESSAGE, ScrollPaneFactory.createScrollPane(messagePanel));

    myDateOrRevisionOrTagSettings = new DateOrRevisionOrTagSettings(new TagsProviderOnEnvironment() {
      @Nullable
      @Override
      protected CvsEnvironment getCvsEnvironment() {
        return getWizard().getSelectedConfiguration();
      }

      @Override
      public String getModule() {
        final CvsElement[] selectedElements = getWizard().getSelectedElements();
        String module = null;
        for (CvsElement element : selectedElements) {
          final String checkoutPath = element.getCheckoutPath();
          if (module == null) {
            module = checkoutPath;
            continue;
          }
          final String commonParentModule = findCommonParentModule(module, checkoutPath);
          if (commonParentModule == null) {
            return super.getModule();
          }
          module = commonParentModule;
        }
        return module;
      }
    }, project);

    init();
  }

  private static String findCommonParentModule(String module1, String module2) {
    int diff = indexOfFirstDifference(module1, module2);
    if (diff == 0) {
      return null;
    }
    if (diff == module1.length()) {
      return module1;
    }
    if (module1.charAt(diff - 1) == '/') {
      return module1.substring(0, diff);
    }
    else {
      int index = module1.lastIndexOf('/', diff);
      if (index < 0) {
        return null;
      }
      return module1.substring(0, index);
    }
  }

  private static int indexOfFirstDifference(@NotNull String a, @NotNull String b) {
    int max = Math.min(a.length(), b.length());
    for (int i = 0; i < max; i++) {
      final char c = a.charAt(i);
      if (c != b.charAt(i)) {
        return i;
      }
    }
    return max;
  }

  @Override
  protected CheckoutWizard getWizard() {
    return (CheckoutWizard)super.getWizard();
  }

  @Override
  public boolean nextIsEnabled() {
    if (myCvsPaths.size() == 1)
      return myCheckoutModeList.getSelectedValue() != null;
    else
      return true;
  }

  @Override
  protected JComponent createComponent() {
    final JPanel result = new JPanel(new BorderLayout(4, 2));
    result.add(myCenterPanel, BorderLayout.CENTER);
    result.add(createOptionsPanel(), BorderLayout.SOUTH);
    return result;
  }

  private JPanel createOptionsPanel() {
    final JPanel result = new JPanel(new VerticalFlowLayout());
    result.add(myMakeNewFilesReadOnly);
    result.add(myPruneEmptyDirectories);
    result.add(myChangeKeywordSubstitutionPanel.getComponent());
    result.add(myDateOrRevisionOrTagSettings.getPanel());
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCheckoutModeList;
  }

  @Override
  public void activate() {
    final File selectedLocation = getWizard().getSelectedLocation();
    final Collection<File> cvsPaths = getSelectedFiles();

    if (!FileUtil.filesEqual(selectedLocation, mySelectedLocation) || !Comparing.equal(cvsPaths, myCvsPaths)) {
      mySelectedLocation = selectedLocation;
      myCvsPaths.clear();
      myCvsPaths.addAll(cvsPaths);

      if (myCvsPaths.size() == 1) {
        show(LIST);
        rebuildList();
      }
      else {
        setStepTitle(CvsBundle.message("info.text.selected.modules.will.be.checked.out.to"));
        final StringBuilder message = composeLocationsMessage();
        myMessage.setText(message.toString());
        show(MESSAGE);
        getWizard().updateButtons();
      }
    }
    else if (selectedLocation == null) {
      getWizard().updateButtons();
    }
    final CvsRootConfiguration configuration = getWizard().getSelectedConfiguration();
    if (myConfiguration != configuration) {
      myDateOrRevisionOrTagSettings.updateFrom(configuration.DATE_OR_REVISION_SETTINGS);
      myConfiguration = configuration;
    }
  }

  private StringBuilder composeLocationsMessage() {
    @NonNls final StringBuilder message = new StringBuilder("<html><p>");
    message.append(mySelectedLocation.getAbsolutePath()).append("</p>");
    for (File file : myCvsPaths) {
      message.append("<p>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-").append(file.getPath()).append("</p>");
    }
    return message;
  }

  private Collection<File> getSelectedFiles() {
    final Collection<File> allFiles = new HashSet<>();
    final CvsElement[] selection = getWizard().getSelectedElements();
    if (selection == null) return allFiles;
    for (CvsElement cvsElement : selection) {
      allFiles.add(new File(cvsElement.getCheckoutPath()));
    }

    final ArrayList<File> result = new ArrayList<>();

    for (File file : allFiles) {
      if (!hasParentIn(allFiles, file)) result.add(file);
    }

    Collections.sort(result, (file, file1) -> file.getPath().compareTo(file1.getPath()));
    return result;
  }

  private static boolean hasParentIn(Collection<File> allFiles, File file) {
    final String filePath = file.getPath();
    for (File file1 : allFiles) {
      if (FileUtil.filesEqual(file1, file)) continue;
      if (filePath.startsWith(file1.getPath())) return true;
    }
    return false;
  }

  private void rebuildList() {
    final File selected = myCvsPaths.iterator().next();
    setStepTitle(CvsBundle.message("dialog.title.check.out.to", selected));
    myCheckoutModeModel.removeAllElements();

    final boolean forFile = getWizard().getSelectedElements()[0] instanceof CvsFile;
    final CheckoutStrategy[] strategies = CheckoutStrategy.createAllStrategies(mySelectedLocation, selected, forFile);
    final Collection<File> results = new HashSet();
    final List<CheckoutStrategy> resultModes = new ArrayList();
    for (CheckoutStrategy strategy : strategies) {
      final File resultFile = strategy.getResult();
      if (resultFile != null && !results.contains(resultFile)) {
        results.add(resultFile);
        resultModes.add(strategy);
      }
    }

    Collections.sort(resultModes);
    for (CheckoutStrategy resultMode : resultModes) {
      myCheckoutModeModel.addElement(resultMode);
    }
    myCheckoutModeList.setSelectedIndex(0);
  }

  private void show(String mode) {
    ((CardLayout)myCenterPanel.getLayout()).show(myCenterPanel, mode);
  }

  public boolean getMakeNewFilesReadOnly() {
    return myMakeNewFilesReadOnly.isSelected();
  }

  public boolean getPruneEmptyDirectories() {
    return myPruneEmptyDirectories.isSelected();
  }

  public boolean useAlternativeCheckoutLocation() {
    if (myCvsPaths.size() == 1) {
      final CheckoutStrategy checkoutStrategy = (CheckoutStrategy)myCheckoutModeList.getSelectedValue();
      return checkoutStrategy.useAlternativeCheckoutLocation();
    }
    else {
      return false;
    }
  }

  public File getCheckoutDirectory() {
    if (myCvsPaths.size() == 1) {
      final CheckoutStrategy checkoutStrategy = (CheckoutStrategy)myCheckoutModeList.getSelectedValue();
      return checkoutStrategy.getCheckoutDirectory();
    }
    else {
      return mySelectedLocation;
    }
  }

  public KeywordSubstitution getKeywordSubstitution() {
    return myChangeKeywordSubstitutionPanel.getKeywordSubstitution();
  }

  public void saveDateOrRevisionSettings(CvsRootConfiguration configuration) {
    myDateOrRevisionOrTagSettings.saveTo(configuration.DATE_OR_REVISION_SETTINGS);
  }
}
