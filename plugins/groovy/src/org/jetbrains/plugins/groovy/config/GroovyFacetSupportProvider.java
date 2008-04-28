/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.ui.GroovyFacetEditor;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author ven
 */
public class GroovyFacetSupportProvider extends FacetTypeFrameworkSupportProvider<GroovyFacet> {

  protected GroovyFacetSupportProvider() {
    super(GroovyFacetType.INSTANCE);
  }

  @NotNull
  public GroovyVersionConfigurable createConfigurable() {
    return new GroovyVersionConfigurable(getVersions(), getDefaultVersion());
  }

  @Nullable
  public String getDefaultVersion() {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    return settings.DEFAULT_GROOVY_LIB_NAME;
  }

  @NotNull
  public String[] getVersions() {
    String[] versions = ContainerUtil.map2Array(GroovyConfigUtils.getGroovyLibraries(), String.class, new Function<Library, String>() {
      public String fun(Library library) {
        return library.getName();
      }
    });
    Arrays.sort(versions, new Comparator<String>() {
      public int compare(String o1, String o2) {
        return o1.compareToIgnoreCase(o2);
      }
    });
    return versions;
  }

  @NotNull
  @NonNls
  public String getLibraryName(final String name) {
    return name;
  }

  @NotNull
  public LibraryInfo[] getLibraries(String selectedVersion) {
    return super.getLibraries(selectedVersion);
  }

  protected void setupConfiguration(GroovyFacet facet, ModifiableRootModel rootModel, String v) {
    /**
     * Everyting is already done in {@link GroovyVersionConfigurable#addSupport}
     * */
  }

  public class GroovyVersionConfigurable extends VersionConfigurable {
    public final GroovyFacetEditor myFacetEditor;

    public JComponent getComponent() {
      return myFacetEditor.createComponent();
    }

    @NotNull
    public LibraryInfo[] getLibraries() {
      return GroovyFacetSupportProvider.this.getLibraries(myFacetEditor.getSelectedVersion());
    }

    @Nullable
    public String getNewSdkPath() {
      return myFacetEditor.getNewSdkPath();
    }

    public boolean addNewSdk() {
      return myFacetEditor.addNewSdk();
    }

    @NonNls
    @NotNull
    public String getLibraryName() {
      return GroovyFacetSupportProvider.this.getLibraryName(myFacetEditor.getSelectedVersion());
    }

    public void addSupport(final Module module, final ModifiableRootModel rootModel, @Nullable Library library) {
      String version = myFacetEditor.getSelectedVersion();
      if (version != null && !myFacetEditor.addNewSdk()) {
        library = LibrariesUtil.getLibraryByName(version);
        if (library != null) {
          LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(library));
        }
      } else if (myFacetEditor.getNewSdkPath() != null) {
        String path = myFacetEditor.getNewSdkPath();
        ValidationResult result = GroovyConfigUtils.isGroovySdkHome(path);
        if (path != null && ValidationResult.OK == result) {
          String name = GroovyConfigUtils.generateNewGroovyLibName(GroovyConfigUtils.getGroovyVersion(path));
          version = name;
          library = GroovyConfigUtils.createGroovyLibrary(path, name, module.getProject(), false);
          LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(library));
        } else {
          Messages.showErrorDialog(GroovyBundle.message("invalid.groovy.sdk.path.message"), GroovyBundle.message("invalid.groovy.sdk.path.text"));
          version = null;
        }
      }
      if (version != null) {
        GroovyConfigUtils.saveGroovyDefaultLibName(version);
      }
      GroovyFacetSupportProvider.this.addSupport(module, rootModel, version, library);
    }

    public GroovyVersionConfigurable(String[] versions, String defaultVersion) {
      super(versions, defaultVersion);
      myFacetEditor = new GroovyFacetEditor(getVersions(), getDefaultVersion());
    }
  }
}
