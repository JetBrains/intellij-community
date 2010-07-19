/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;

import javax.swing.*;

public class XmlAttributePanel extends AbstractInjectionPanel<XmlAttributeInjection> {

  private JPanel myRoot;

  // read by reflection
  LanguagePanel myLanguagePanel;
  TagPanel myTagPanel;
  AdvancedXmlPanel myAdvancedPanel;

  private EditorTextField myLocalName;
  private ComboBox myNamespace;

  public XmlAttributePanel(XmlAttributeInjection injection, Project project) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987

    myNamespace.setModel(TagPanel.createNamespaceUriModel(project));

    init(injection.copy());

    // be sure to add the listener after initializing the textfield's value
    myLocalName.getDocument().addDocumentListener(new TreeUpdateListener());
  }

  public JPanel getComponent() {
    return myRoot;
  }

  protected void resetImpl() {
    myLocalName.setText(myOrigInjection.getAttributeName());
    myNamespace.getEditor().setItem(myOrigInjection.getAttributeNamespace());
  }

  protected void apply(XmlAttributeInjection i) {
    i.setAttributeName(myLocalName.getText());
    i.setAttributeNamespace(getNamespace());
  }

  private String getNamespace() {
    final String s = (String)myNamespace.getEditor().getItem();
    return s != null ? s : "";
  }

  private void createUIComponents() {
    myLanguagePanel = new LanguagePanel(myProject, myOrigInjection);
    myTagPanel = new TagPanel(myProject, myOrigInjection);
    myAdvancedPanel = new AdvancedXmlPanel(myProject, myOrigInjection);

    myLocalName = new LanguageTextField(RegExpLanguage.INSTANCE, myProject, myOrigInjection.getAttributeName());

    myNamespace = new ComboBox(200);
  }

  private void $$$setupUI$$$() {
  }
}
