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

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.ui.EditorTextField;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.JspSupportProxy;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import com.intellij.ui.LanguageTextField;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TagPanel extends AbstractInjectionPanel<AbstractTagInjection> {
  public static final Key<List<String>> URI_MODEL = Key.create("URI_MODEL");

  private JPanel myRoot;

  private EditorTextField myLocalName;
  private ComboBox myNamespace;
  private JCheckBox myApplyRecursivelyCheckBox;

  public TagPanel(Project project, AbstractTagInjection injection) {
    super(injection, project);
    $$$setupUI$$$();

    myNamespace.setModel(createNamespaceUriModel(myProject));
    myLocalName.getDocument().addDocumentListener(new TreeUpdateListener());
  }

  public static ComboBoxModel createNamespaceUriModel(Project project) {
    final List<String> data = project.getUserData(URI_MODEL);
    if (data != null) {
      return new DefaultComboBoxModel(data.toArray());
    }

    final List<String> urls = new ArrayList<String>(Arrays.asList(ExternalResourceManager.getInstance().getResourceUrls(null, true)));
    Collections.sort(urls);

    final JspSupportProxy jspSupport = JspSupportProxy.getInstance();
    if (jspSupport != null) {
      final List<String> tlds = new ArrayList<String>();
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (final Module module : modules) {
        final String[] tldUris = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
          public String[] compute() {
            return jspSupport.getPossibleTldUris(module);
          }
        });
        for (String uri : tldUris) {
          if (!tlds.contains(uri)) {
            tlds.add(uri);
          }
        }
      }
      Collections.sort(tlds);

      // TLD URIs are intentionally kept above the other URIs to make it easier to find them
      urls.addAll(0, tlds);
    }

    project.putUserData(URI_MODEL, urls);
    return new DefaultComboBoxModel(urls.toArray());
  }

  public JPanel getComponent() {
    return myRoot;
  }

  protected void resetImpl() {
    myLocalName.setText(myOrigInjection.getTagName());
    myNamespace.getEditor().setItem(myOrigInjection.getTagNamespace());
    final boolean isXmlTag = myOrigInjection instanceof XmlTagInjection;
    myApplyRecursivelyCheckBox.setVisible(isXmlTag);
    if (isXmlTag) {
      myApplyRecursivelyCheckBox.setSelected(((XmlTagInjection)myOrigInjection).isApplyToSubTagTexts());
    }
  }

  protected void apply(AbstractTagInjection i) {
    i.setTagName(myLocalName.getText());
    i.setTagNamespace(getNamespace());
    if (i instanceof XmlTagInjection) {
      ((XmlTagInjection)i).setApplyToSubTagTexts(myApplyRecursivelyCheckBox.isSelected());
    }
  }

  private String getNamespace() {
    final String s = (String)myNamespace.getEditor().getItem();
    return s != null ? s : "";
  }

  private void createUIComponents() {
    myLocalName = new LanguageTextField(RegExpLanguage.INSTANCE, myProject, myOrigInjection.getTagName());
    myNamespace = new ComboBox(200);
  }

  private void $$$setupUI$$$() {
  }
}

