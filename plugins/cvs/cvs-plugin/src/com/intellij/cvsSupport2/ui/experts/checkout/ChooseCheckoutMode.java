/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.cvsBrowser.CvsElement;
import com.intellij.cvsSupport2.cvsBrowser.CvsFile;
import com.intellij.cvsSupport2.ui.ChangeKeywordSubstitutionPanel;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.checkout.CheckoutStrategy;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
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
  private final Collection<File> myCvsPaths = new ArrayList<File>();
  private final DefaultListModel myCheckoutModeModel = new DefaultListModel();
  private final JList myCheckoutModeList = new JBList(myCheckoutModeModel);
  private final JCheckBox myMakeNewFilesReadOnly = new JCheckBox(CvsBundle.message("checkbox.make.new.files.read.only"));
  private final JCheckBox myPruneEmptyDirectories = new JCheckBox(CvsBundle.message("checkbox.prune.empty.directories"));
  private final ChangeKeywordSubstitutionPanel myChangeKeywordSubstitutionPanel;
  private final CheckoutWizard myOwner;

  private final JPanel myCenterPanel = new JPanel(new CardLayout());

  private static final Icon FOLDER_ICON = PlatformIcons.DIRECTORY_CLOSED_ICON;

  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.cvsSupport2.ui.experts.checkout.ChooseCheckoutMode");
  @NonNls public static final String LIST = "LIST";
  @NonNls public static final String MESSAGE = "MESSSAGE";
  private final JLabel myMessage = new JLabel(DUMMY_LABEL_TEXT);
  @NonNls private static final String DUMMY_LABEL_TEXT = "XXX";


  public ChooseCheckoutMode(CheckoutWizard wizard) {
    super("###", wizard);
    myOwner = wizard;
    myCheckoutModeList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        CheckoutStrategy checkoutStrategy = (CheckoutStrategy)value;
        append(checkoutStrategy.getResult().getAbsolutePath(), new SimpleTextAttributes(Font.PLAIN,
                                                                                        list.getForeground()));
        setIcon(FOLDER_ICON);
      }
    });
    myCheckoutModeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myOwner.updateStep();
      }
    });

    myCheckoutModeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    CvsApplicationLevelConfiguration config = CvsApplicationLevelConfiguration.getInstance();
    myMakeNewFilesReadOnly.setSelected(config.MAKE_CHECKED_OUT_FILES_READONLY);
    myPruneEmptyDirectories.setSelected(config.CHECKOUT_PRUNE_EMPTY_DIRECTORIES);
    myChangeKeywordSubstitutionPanel =
      new ChangeKeywordSubstitutionPanel(KeywordSubstitution.getValue(config.CHECKOUT_KEYWORD_SUBSTITUTION));

    myCenterPanel.add(LIST, ScrollPaneFactory.createScrollPane(myCheckoutModeList));
    JPanel messagePanel = new JPanel(new BorderLayout(2,4));
    messagePanel.add(myMessage, BorderLayout.NORTH);
    messagePanel.setBackground(UIUtil.getTableBackground());
    myMessage.setBackground(UIUtil.getTableBackground());
    myCenterPanel.add(MESSAGE, ScrollPaneFactory.createScrollPane(messagePanel));

    init();
  }

  protected void dispose() {
  }

  public boolean nextIsEnabled() {
    if (myCvsPaths.size() == 1)
      return myCheckoutModeList.getSelectedValue() != null;
    else
      return true;
  }

  protected JComponent createComponent() {
    JPanel result = new JPanel(new BorderLayout(4, 2));
    result.add(myCenterPanel, BorderLayout.CENTER);
    result.add(createOptionsPanel(), BorderLayout.SOUTH);
    return result;
  }

  private JPanel createOptionsPanel() {
    JPanel result = new JPanel(new GridLayout(0, 1));
    result.add(myMakeNewFilesReadOnly);
    result.add(myPruneEmptyDirectories);

    result.add(myChangeKeywordSubstitutionPanel.getPanel());

    return result;
  }

  public Component getPreferredFocusedComponent() {
    return myCheckoutModeList;
  }

  public boolean setActive() {
    File selectedLocation = myOwner.getSelectedLocation();
    Collection<File> cvsPaths = getSelectedFiles();

    if ((!Comparing.equal(selectedLocation, mySelectedLocation)) ||
        (!Comparing.equal(cvsPaths, myCvsPaths)) && (selectedLocation != null)) {
      mySelectedLocation = selectedLocation;
      LOG.assertTrue(mySelectedLocation != null);
      myCvsPaths.clear();
      myCvsPaths.addAll(cvsPaths);

      if (myCvsPaths.size() == 1) {
        show(LIST);
        rebuildList();
      }
      else {
        setStepTitle(CvsBundle.message("info.text.selected.modules.will.be.checked.out.to"));
        StringBuffer message = composeLocationsMessage();
        myMessage.setText(message.toString());
        show(MESSAGE);
        getWizard().enableNextAndFinish();
      }
    }
    else if (selectedLocation == null) {
      getWizard().disableNextAndFinish();
    }

    return true;
  }

  private StringBuffer composeLocationsMessage() {
    @NonNls StringBuffer message = new StringBuffer();
    message.append("<html>");
    message.append("<p>");
    message.append(mySelectedLocation.getAbsolutePath());
    message.append("</p>");
    for (File file : myCvsPaths) {
      message.append("<p>");
      message.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-");
      message.append(file.getPath());
      message.append("</p>");
    }
    return message;
  }

  private Collection<File> getSelectedFiles() {
    Collection<File> allFiles = new HashSet<File>();
    CvsElement[] selection = myOwner.getSelectedElements();
    if (selection == null) return allFiles;
    for (CvsElement cvsElement : selection) {
      allFiles.add(new File(cvsElement.getCheckoutPath()));
    }

    ArrayList<File> result = new ArrayList<File>();

    for (File file : allFiles) {
      if (!hasParentIn(allFiles, file)) result.add(file);
    }

    Collections.sort(result, new Comparator<File>(){
      public int compare(File file, File file1) {
        return file.getPath().compareTo(file1.getPath());
      }
    });
    return result;
  }

  private static boolean hasParentIn(Collection<File> allFiles, File file) {
    String filePath = file.getPath();
    for (File file1 : allFiles) {
      if (file1.equals(file)) continue;
      if (filePath.startsWith(file1.getPath())) return true;
    }
    return false;
  }

  private void rebuildList() {
    File selected = myCvsPaths.iterator().next();
    setStepTitle(CvsBundle.message("dialog.title.check.out.to", selected));
    myCheckoutModeModel.removeAllElements();

    CheckoutStrategy[] strategies = CheckoutStrategy.createAllStrategies(mySelectedLocation,
                                                                         selected,
                                                                         myOwner.getSelectedElements()[0] instanceof CvsFile);
    Collection<File> results = new HashSet();
    List<CheckoutStrategy> resultModes = new ArrayList();
    for (CheckoutStrategy strategy : strategies) {
      File resultFile = strategy.getResult();
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
      return ((CheckoutStrategy)myCheckoutModeList.getSelectedValue()).useAlternativeCheckoutLocation();
    }
    else {
      return false;
    }
  }

  public File getCheckoutDirectory() {
    if (myCvsPaths.size() == 1) {
      return ((CheckoutStrategy)myCheckoutModeList.getSelectedValue()).getCheckoutDirectory();
    }
    else {
      return mySelectedLocation;
    }
  }

  public KeywordSubstitution getKeywordSubstitution() {
    return myChangeKeywordSubstitutionPanel.getKeywordSubstitution();
  }
}
