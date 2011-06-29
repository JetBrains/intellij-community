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

package org.jetbrains.android.run;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.prefs.AndroidLocation.FOLDER_AVD;

/**
 * @author Eugene.Kudelevsky
 * Date: May 9, 2009
 */
public class CreateAvdDialog extends DialogWrapper {
  private JTextField myNameField;
  private JComboBox myTargetBox;
  private JComboBox mySkinField;
  private JPanel myPanel;
  private JLabel myAvdInfoLink;
  private JLabel myAvdInfoLabel;
  private JRadioButton mySdCardSizeRadioButton;
  private JRadioButton mySdCardFileRadioButton;
  private JSpinner mySdCardSizeSpinner;
  private JComboBox mySdCardSizeUnitCombo;
  private TextFieldWithBrowseButton mySdCardFileTextField;
  private final AvdManager myAvdManager;
  private AvdManager.AvdInfo myCreatedAvd;

  private final Project myProject;

  private static final String MB_UNIT = "MiB";
  private static final String MB_SUFFIX = "M";

  private static final String KB_UNIT = "KiB";
  private static final String KB_SUFFIX = "K";

  private static class Size {
    final int myWidth;
    final int myHeight;

    private Size(int width, int height) {
      myWidth = width;
      myHeight = height;
    }
  }

  private static final String DEFAULT_SKIN = "HVGA";
  private static Map<String, Size> displaySizes;

  private static void initializeDisplaySizes() {
    // portrait is default
    displaySizes = new HashMap<String, Size>();
    displaySizes.put("HVGA", new Size(320, 480));
    displaySizes.put("QVGA", new Size(240, 320));
    displaySizes.put("WQVGA", new Size(240, 400));
    displaySizes.put("WQVGA400", new Size(240, 400));
    displaySizes.put("WQVGA432", new Size(240, 432));
    displaySizes.put("FWQVGA", new Size(240, 432));
    displaySizes.put("WVGA800", new Size(480, 800));
    displaySizes.put("WVGA854", new Size(480, 854));
  }

  @NotNull
  private String generateAvdName() {
    String prefix = "MyAvd";
    for (int i = 0; ; i++) {
      String candidate = prefix + i;
      if (myAvdManager.getAvd(candidate, false) == null) {
        return candidate;
      }
    }
  }

  @Nullable
  private static String getSkinInfo(@NotNull String skinName) {
    int index = skinName.indexOf('-');
    String nameWithoutSuffix = index >= 0 ? skinName.substring(0, index) : skinName;
    if (displaySizes == null) {
      initializeDisplaySizes();
    }
    Size size = displaySizes.get(nameWithoutSuffix);
    if (size != null) {
      if (skinName.endsWith("-L")) {
        return size.myHeight + "x" + size.myWidth + ", landscape";
      }
      if (skinName.endsWith("-P")) {
        return size.myWidth + "x" + size.myHeight + ", portrait";
      }
      return size.myWidth + "x" + size.myHeight;
    }
    return null;
  }

  private static boolean containsDefaultSkinWithSuffix(@NotNull String[] skinNames) {
    for (String skinName : skinNames) {
      if (skinName != null && !skinName.equals(DEFAULT_SKIN) && skinName.startsWith(DEFAULT_SKIN)) {
        return true;
      }
    }
    return false;
  }

  private static String getSkinPresentation(@NotNull String skinName) {
    String info = getSkinInfo(skinName);
    return info != null ? skinName + " (" + info + ')' : skinName;
  }

  public CreateAvdDialog(@NotNull Project project,
                         @NotNull AndroidFacet facet,
                         @NotNull AvdManager manager,
                         boolean onlyCompatibleTargets,
                         boolean showAvdInfo) {
    super(project, true);
    myProject = project;
    setTitle(AndroidBundle.message("create.avd.dialog.title"));
    init();
    myAvdManager = manager;
    final AndroidSdk sdk = facet.getConfiguration().getAndroidSdk();
    assert sdk != null;
    IAndroidTarget[] targets = sdk.getTargets();
    myTargetBox.setModel(new DefaultComboBoxModel(targets));
    myTargetBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Object selected = myTargetBox.getSelectedItem();
        mySkinField.setEnabled(selected != null);
        if (selected != null) {
          IAndroidTarget target = (IAndroidTarget)selected;
          List<String> skinsToAdd = new ArrayList<String>();
          String[] skins = target.getSkins();
          for (String skin : skins) {
            // skip default HVGA skin without suffix
            if (DEFAULT_SKIN.equals(skin) && containsDefaultSkinWithSuffix(skins)) {
              continue;
            }
            skinsToAdd.add(skin);
          }
          mySkinField.setModel(new CollectionComboBoxModel(skinsToAdd, null));
        }
      }
    });
    mySkinField.setRenderer(new ListCellRendererWrapper(mySkinField.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        String presentation = value != null ? getSkinPresentation((String)value) : null;
        setText(presentation);
      }
    });
    myTargetBox.setRenderer(new ListCellRendererWrapper<IAndroidTarget>(myTargetBox.getRenderer()) {
      @Override
      public void customize(JList list, IAndroidTarget value, int index, boolean selected, boolean hasFocus) {
        setText(AndroidSdkUtils.getPresentableTargetName(value));
      }
    });
    IAndroidTarget target = facet.getConfiguration().getAndroidTarget();
    if (target != null) {
      myTargetBox.setSelectedItem(target);
      if (onlyCompatibleTargets) {
        myTargetBox.setEnabled(false);
      }
    }
    else if (targets.length > 0) {
      myTargetBox.setSelectedItem(targets[0]);
    }
    myNameField.setText(generateAvdName());
    final String url = "http://developer.android.com/guide/developing/tools/avd.html";
    myAvdInfoLink.setText("<html>\n" +
                          "   <body>\n" +
                          "     <p style=\"margin-top: 0;\">\n" +
                          "<a href=\"" +
                          url +
                          "\">More information about AVDs</a>\n" +
                          "     </p>\n" +
                          "   </body>\n" +
                          " </html>");
    myAvdInfoLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myAvdInfoLink.setVisible(showAvdInfo);
    myAvdInfoLabel.setVisible(showAvdInfo);
    myAvdInfoLink.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed()) return;
        try {
          BrowserUtil.launchBrowser(url);
        }
        catch (IllegalThreadStateException ex) {
          /* not a problem */
        }
        e.consume();
      }
    });
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSdCardElements();
      }
    };
    mySdCardFileRadioButton.addActionListener(listener);
    mySdCardSizeRadioButton.addActionListener(listener);
    mySdCardFileRadioButton.setSelected(true);
    updateSdCardElements();
    mySdCardSizeUnitCombo.setModel(new DefaultComboBoxModel(new Object[]{MB_UNIT, KB_UNIT}));
    mySdCardSizeUnitCombo.setSelectedItem(MB_UNIT);
    mySdCardSizeUnitCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSpinner();
      }
    });
    updateSpinner();
    mySdCardFileTextField
      .addBrowseFolderListener(AndroidBundle.message("android.create.avd.dialog.sdcard.file.browser.title"), null, project,
                               new FileChooserDescriptor(true, false, false, false, false, false));
  }

  private void updateSpinner() {
    boolean mb = MB_UNIT.equals(mySdCardSizeUnitCombo.getSelectedItem());
    long max = mb ? 999999999 : 999999999999L;
    long min = mb ? 9 : 9 * 1024;
    Object o = mySdCardSizeSpinner.getValue();
    long value = min;
    if (o instanceof Number) {
      value = ((Number)o).longValue();
      if (value < min) {
        value = min;
      }
      else if (value > max) {
        value = max;
      }
    }
    mySdCardSizeSpinner.setModel(new SpinnerNumberModel(new Long(value), new Long(min), new Long(max), new Long(1)));
  }

  private void updateSdCardElements() {
    boolean file = mySdCardFileRadioButton.isSelected();
    mySdCardFileTextField.setEnabled(file);
    mySdCardSizeSpinner.setEnabled(!file);
    mySdCardSizeUnitCombo.setEnabled(!file);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected void doOKAction() {
    if (myNameField.getText().length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("specify.avd.name.error"));
      return;
    }
    else if (myTargetBox.getSelectedItem() == null) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("select.target.dialog.text"));
      return;
    }
    String avdName = myNameField.getText();
    AvdManager.AvdInfo info = myAvdManager.getAvd(avdName, false);
    if (info != null) {
      boolean replace = Messages
                          .showYesNoDialog(myPanel, AndroidBundle.message("replace.avd.question", avdName),
                                           AndroidBundle.message("create.avd.dialog.title"),
                                           Messages.getQuestionIcon()) == 0;
      if (!replace) return;
    }
    File avdFolder;
    try {
      avdFolder = new File(AndroidLocation.getFolder() + FOLDER_AVD, avdName + AvdManager.AVD_FOLDER_EXTENSION);
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Messages.showErrorDialog(myPanel, e.getMessage(), "Error");
      return;
    }
    super.doOKAction();
    IAndroidTarget selectedTarget = (IAndroidTarget)myTargetBox.getSelectedItem();
    String skin = (String)mySkinField.getSelectedItem();
    String sdCard = getSdCardParameter();
    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    myCreatedAvd = myAvdManager.createAvd(avdFolder, avdName, selectedTarget, skin, sdCard, null, true, log);
    if (log.getErrorMessage().length() > 0) {
      Messages.showErrorDialog(myProject, log.getErrorMessage(), AndroidBundle.message("android.avd.error.title"));
    }
  }

  @Nullable
  private String getSdCardParameter() {
    if (mySdCardFileRadioButton.isSelected()) {
      String text = mySdCardFileTextField.getText();
      return text.length() > 0 ? text : null;
    }
    String value = mySdCardSizeSpinner.getValue().toString();
    if (value.length() == 0 || "0".equals(value)) {
      return null;
    }
    String suffix = MB_UNIT.equals(mySdCardSizeUnitCombo.getSelectedItem()) ? MB_SUFFIX : KB_SUFFIX;
    return value + suffix;
  }

  @Nullable
  public AvdManager.AvdInfo getCreatedAvd() {
    return myCreatedAvd;
  }

  @Override
  protected String getHelpId() {
    return "reference.android.createAVD";
  }
}
