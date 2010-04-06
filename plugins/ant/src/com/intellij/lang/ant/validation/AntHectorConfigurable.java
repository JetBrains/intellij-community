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
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.impl.AntImportsIndex;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
public class AntHectorConfigurable extends HectorComponentPanel {
  @NonNls
  private static final String NONE = "<None>";
  @NonNls 
  public static final String CONTEXTS_COMBO_KEY = "AntContextsComboBox";

  private final AntFile myFile;
  private final String myLocalPath;
  private final Map<String, AntFile> myPathToFileMap = new HashMap<String, AntFile>();
  private String myOriginalContext = NONE;
  
  private JComboBox myCombo;
  private final GlobalSearchScope myFileFilter;

  public AntHectorConfigurable(AntFile file) {
    myFile = file;
    final VirtualFile selfVFile = myFile.getVirtualFile();
    myLocalPath = PathUtil.getLocalPath(selfVFile);
    
    final Project project = file.getProject();
    myFileFilter = GlobalSearchScope.projectScope(project);
  }

  public boolean canClose() {
    return !myCombo.isPopupVisible();
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("File Context"));
    myCombo = new ComboBox();
    myCombo.putClientProperty(CONTEXTS_COMBO_KEY, Boolean.TRUE);
    panel.add(
        new JLabel("Included into:"), 
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0)
    );
    panel.add(
        myCombo, 
        new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0)
    );

    final FileBasedIndex fbi = FileBasedIndex.getInstance();
    final Collection<VirtualFile> antFiles = fbi.getContainingFiles(AntImportsIndex.INDEX_NAME, AntImportsIndex.ANT_FILES_WITH_IMPORTS_KEY, myFileFilter);
    for (VirtualFile file : antFiles) {
      final AntFile antFile = AntSupport.toAntFile(file, myFile.getProject());
      if (antFile != null && !antFile.equals(myFile)) {
        final String path = PathUtil.getLocalPath(file);
        final AntFile previous = myPathToFileMap.put(path, antFile);
        assert previous == null;
      }
    }
    final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(myFile.getProject());
    for (AntBuildFile buildFile : antConfig.getBuildFiles()) {
      final AntFile antFile = (AntFile)buildFile.getAntFile();
      final String path = antFile != null? PathUtil.getLocalPath(antFile.getVirtualFile()) : null;
      if (path != null && !myPathToFileMap.containsKey(path) && !FileUtil.pathsEqual(path, myLocalPath)) {
        final AntProject antProject = antFile.getAntProject();
        if (antProject != null && antProject.getImportedFiles().length > 0) {
          myPathToFileMap.put(path, antFile);
        }
      }
    }

    final List<String> paths = new ArrayList<String>(myPathToFileMap.keySet());
    Collections.sort(paths, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareTo(o2);
      }
    });

    myCombo.addItem(NONE);
    for (String path : paths) {
      myCombo.addItem(path);
    }

    final AntFile currentContext = antConfig.getContextFile(myFile);
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

  public boolean isModified() {
    return !FileUtil.pathsEqual(myOriginalContext, (String)myCombo.getSelectedItem());
  }

  public void apply() throws ConfigurationException {
    applyItem((String)myCombo.getSelectedItem());
  }

  public void reset() {
    applyItem(myOriginalContext);
  }

  private void applyItem(final String contextStr) {
    AntFile context = null;
    if (!NONE.equals(contextStr)) {
      context = myPathToFileMap.get(contextStr);
      assert context != null;
    }
    AntConfigurationBase.getInstance(myFile.getProject()).setContextFile(myFile, context);
  }

  public void disposeUIResources() {
    myPathToFileMap.clear();
  }
}
