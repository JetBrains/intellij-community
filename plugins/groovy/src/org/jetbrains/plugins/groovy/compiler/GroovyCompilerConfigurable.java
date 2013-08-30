/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public class GroovyCompilerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JTextField myHeapSize;
  private JPanel myMainPanel;
  private JPanel myExcludesPanel;
  private JBCheckBox myInvokeDynamicSupportCB;

  private final ExcludedEntriesConfigurable myExcludes;
  private final GroovyCompilerConfiguration myConfig;

  public GroovyCompilerConfigurable(Project project) {
    myConfig = GroovyCompilerConfiguration.getInstance(project);
    myExcludes = createExcludedConfigurable(project);
  }

  public ExcludedEntriesConfigurable getExcludes() {
    return myExcludes;
  }

  private ExcludedEntriesConfigurable createExcludedConfigurable(final Project project) {
    final ExcludedEntriesConfiguration configuration = myConfig.getExcludeFromStubGeneration();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true) {
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && !index.isIgnored(file);
      }
    };
    descriptor.setRoots(ContainerUtil.concat(
      ContainerUtil.map(ModuleManager.getInstance(project).getModules(), new Function<Module, List<VirtualFile>>() {
        @Override
        public List<VirtualFile> fun(final Module module) {
          return ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
        }
      })));
    return new ExcludedEntriesConfigurable(project, descriptor, configuration);
  }


  @NotNull
  public String getId() {
    return "Groovy compiler";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return "Groovy Compiler";
  }

  public String getHelpTopic() {
    return "reference.projectsettings.compiler.groovy";
  }

  public JComponent createComponent() {
    myExcludesPanel.add(myExcludes.createComponent());
    return myMainPanel;
  }

  public boolean isModified() {
    return !Comparing.equal(myConfig.getHeapSize(), myHeapSize.getText()) ||
           myInvokeDynamicSupportCB.isSelected() != myConfig.isInvokeDynamic() ||
           myExcludes.isModified();
  }

  public void apply() throws ConfigurationException {
    myExcludes.apply();
    myConfig.setHeapSize(myHeapSize.getText());
    myConfig.setInvokeDynamic(myInvokeDynamicSupportCB.isSelected());
  }

  public void reset() {
    myHeapSize.setText(myConfig.getHeapSize());
    myInvokeDynamicSupportCB.setSelected(myConfig.isInvokeDynamic());
    myExcludes.reset();
  }

  public void disposeUIResources() {
    myExcludes.disposeUIResources();
  }
}
