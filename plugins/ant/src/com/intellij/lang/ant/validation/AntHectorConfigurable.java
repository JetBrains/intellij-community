// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntImportsIndex;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class AntHectorConfigurable extends HectorComponentPanel {
  @NonNls
  public static final String CONTEXTS_COMBO_KEY = "AntContextsComboBox";

  private final XmlFile myFile;
  private final @NlsSafe String myLocalPath;
  private final Map<@NlsSafe String, XmlFile> myPathToFileMap = new HashMap<>();
  private final @Nls String myNoneText = AntBundle.message("combobox.hector.configurable.none.text");
  private @NlsSafe String myOriginalContext = myNoneText;

  private JComboBox<String> myCombo;
  private final GlobalSearchScope myFileFilter;
  private final Project myProject;

  public AntHectorConfigurable(XmlFile file) {
    myFile = file;
    myProject = file.getProject();
    final VirtualFile selfVFile = myFile.getVirtualFile();
    myLocalPath = PathUtil.getLocalPath(selfVFile);
    myFileFilter = GlobalSearchScope.projectScope(myProject);
  }

  @Override
  public boolean canClose() {
    return !myCombo.isPopupVisible();
  }

  @Override
  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(UIUtil.getToolTipActionBackground());
    panel.setBorder(IdeBorderFactory.createTitledBorder(AntBundle.message("configurable.hector.border.title.file.context"), false));
    myCombo = new ComboBox<>();
    myCombo.putClientProperty(CONTEXTS_COMBO_KEY, Boolean.TRUE);
    GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
                                                            GridBagConstraints.WEST, GridBagConstraints.NONE, JBInsets.create(5, 0), 0, 0);
    panel.add(new JLabel(AntBundle.message("configurable.hector.included.into")), constraints);
    panel.add(
      myCombo,
      new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insets(5, 5, 5, 0), 0, 0));

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final FileBasedIndex fbi = FileBasedIndex.getInstance();
    final Collection<VirtualFile> antFiles = fbi.getContainingFiles(AntImportsIndex.INDEX_NAME, AntImportsIndex.ANT_FILES_WITH_IMPORTS_KEY, myFileFilter);

    for (VirtualFile file : antFiles) {
      final PsiFile psiFile = psiManager.findFile(file);
      if (!(psiFile instanceof XmlFile)) {
        continue;
      }
      final XmlFile xmlFile = (XmlFile)psiFile;
      if (!xmlFile.equals(myFile) && AntDomFileDescription.isAntFile(xmlFile)) {
        final String path = PathUtil.getLocalPath(file);
        final XmlFile previous = myPathToFileMap.put(path, xmlFile);
        assert previous == null;
      }
    }

    final List<String> paths = new ArrayList<>(myPathToFileMap.keySet());
    paths.sort(Comparator.naturalOrder());

    myCombo.addItem(myNoneText);
    for (@NlsSafe String path : paths) {
      myCombo.addItem(path);
    }

    final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(myProject);
    final XmlFile currentContext = antConfig.getContextFile(myFile);
    if (currentContext != null) {
      final VirtualFile vFile = currentContext.getVirtualFile();

      assert vFile != null;

      final String path = PathUtil.getLocalPath(vFile);
      if (!FileUtil.pathsEqual(path, myLocalPath)) {
        myOriginalContext = path;
      }
    }
    myCombo.setSelectedItem(myOriginalContext);

    return panel;
  }

  @Override
  public boolean isModified() {
    return !FileUtil.pathsEqual(myOriginalContext, (String)myCombo.getSelectedItem());
  }

  @Override
  public void apply() {
    applyItem((String)myCombo.getSelectedItem());
  }

  @Override
  public void reset() {
    applyItem(myOriginalContext);
  }

  private void applyItem(final String contextStr) {
    XmlFile context = null;
    if (!myNoneText.equals(contextStr)) {
      context = myPathToFileMap.get(contextStr);
      assert context != null;
    }
    AntConfigurationBase.getInstance(myProject).setContextFile(myFile, context);
  }

  @Override
  public void disposeUIResources() {
    myPathToFileMap.clear();
  }
}
