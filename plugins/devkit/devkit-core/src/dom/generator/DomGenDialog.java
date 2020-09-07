// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.devkit.DevKitBundle;

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
    setTitle(DevKitBundle.message("dom.generator.dialog.title"));
    init();
    getOKAction().putValue(Action.NAME, DevKitBundle.message("dom.generator.generate.button"));
  }

  @Override
  protected JComponent createCenterPanel() {
    return comp;
  }

  @Override
  protected void doOKAction() {
    if (!panel.validate()) return;
    final String location = panel.getLocation();
    ModelLoader loader = StringUtil.toLowerCase(location).endsWith(".xsd") ? new XSDModelLoader() : new DTDModelLoader();
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
      throw new IllegalStateException("Model generation failed", e);
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
