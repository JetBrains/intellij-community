/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class DomGenDialog extends DialogWrapper{
  final DomGenPanel panel;
  final JComponent comp;

  protected DomGenDialog(Project project) {
    super(project);
    panel = new DomGenPanel(project);
    comp = panel.getComponent();
    panel.restore();
    setTitle("Generate DOM Model From XSD or DTD");
    init();
    getOKAction().putValue(Action.NAME, "Generate");
  }

  @Override
  protected JComponent createCenterPanel() {
    return comp;
  }

  @Override
  protected void doOKAction() {
    if (!panel.validate()) return;
    final String location = panel.getLocation();
    ModelLoader loader = location.toLowerCase().endsWith(".xsd") ? new XSDModelLoader() : new DTDModelLoader();
    final JetBrainsEmitter emitter = new JetBrainsEmitter();
    final MergingFileManager fileManager = new MergingFileManager();
    if (panel.getAuthor().trim().length() > 0) {
      emitter.setAuthor(panel.getAuthor());
    }
    if (panel.isUseQualifiedClassNames()) {
      emitter.enableQualifiedClassNames();
    }
    final ModelGen modelGen = new ModelGen(loader, emitter, fileManager);
    final NamespaceDesc desc = panel.getNamespaceDescriptor();
    modelGen.setConfig(desc.name, location, desc, panel.getSkippedSchemas());
    try {
      final File output = new File(panel.getOutputDir());
      modelGen.perform(output, new File(location).getParentFile());
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    panel.saveAll();
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    panel.saveAll();
    super.doCancelAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
