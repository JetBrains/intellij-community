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

package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private JPanel myPanel;
  private JPanel myManagedLibrariesPanel;
  private final ManagedLibrariesEditor myManagedLibrariesEditor;

  public GroovyFacetTab(final FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myManagedLibrariesEditor = new ManagedLibrariesEditor(editorContext, validatorsManager, AbstractGroovyLibraryManager.EP_NAME);

    myManagedLibrariesPanel.add(myManagedLibrariesEditor.getComponent());

    myManagedLibrariesEditor.shouldHaveLibrary(new Condition<Library>() {
      public boolean value(Library libraryManager) {
        final VirtualFile[] files = editorContext.getLibraryFiles(libraryManager, OrderRootType.CLASSES);
        return LibrariesUtil.getGroovyLibraryHome(files) != null;
      }
    }, "Groovy-containing libraries are missing");
  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() {
  }

  public void reset() {
    myManagedLibrariesEditor.updateLibraryList();
  }

  public void disposeUIResources() {
  }

}
