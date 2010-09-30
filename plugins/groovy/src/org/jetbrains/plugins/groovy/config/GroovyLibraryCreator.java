/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryCreator;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.ui.GroovyFacetEditor;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class GroovyLibraryCreator extends CustomLibraryCreator {
  private final GroovyLibraryDescription myDescription;

  public GroovyLibraryCreator() {
    myDescription = new GroovyLibraryDescription();
  }

  @Override
  public String getDisplayName() {
    return "Groovy";
  }

  @Override
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NotNull
  @Override
  public CustomLibraryDescription getDescription() {
    return myDescription;
  }

  private static class GroovyLibraryDescription extends CustomLibraryDescription {
    private final Condition<List<VirtualFile>> myCondition;

    public GroovyLibraryDescription() {
      myCondition = new Condition<List<VirtualFile>>() {
        @Override
        public boolean value(List<VirtualFile> virtualFiles) {
          return LibraryPresentationManager.getInstance().isLibraryOfKind(virtualFiles, GroovyLibraryPresentationProvider.GROOVY_KIND);
        }
      };
    }

    @NotNull
    @Override
    public String getDefaultLibraryName() {
      return "xxx";
    }

    @NotNull
    @Override
    public Condition<List<VirtualFile>> getSuitableLibraryCondition() {
      return myCondition;
    }

    @Override
    public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
      final VirtualFile[] files = FileChooser.chooseFiles(parentComponent, FileChooserDescriptorFactory.createSingleFolderDescriptor());
      if (files.length != 1) return null;

      final VirtualFile dir = files[0];
      final AbstractGroovyLibraryManager manager = GroovyFacetEditor.findManager(dir);
      if (manager == null) return null;

      final String path = dir.getPath();
      final String sdkVersion = manager.getSDKVersion(path);
      if (AbstractConfigUtils.UNDEFINED_VERSION.equals(sdkVersion)) {
        return null;
      }

      return new NewLibraryConfiguration(manager.getLibraryPrefix() + "-" + sdkVersion) {
        @Override
        public void addRoots(@NotNull LibraryEditor editor) {
          manager.fillLibrary(path, editor);
        }
      };
    }
  }
}
