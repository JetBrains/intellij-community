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
package com.intellij.android.designer.designSurface;

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.PropertyParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class AndroidPasteFactory implements ComponentPasteFactory {
  private final Project myProject;
  private final MetaManager myMetaManager;
  private final String myXmlComponents;
  private final Map<RadViewComponent, Element> myProperties = new HashMap<RadViewComponent, Element>();
  private List<RadComponent> myComponents;

  public AndroidPasteFactory(Project project, String xmlComponents) {
    myProject = project;
    myMetaManager = ViewsMetaManager.getInstance(project);
    myXmlComponents = xmlComponents;
  }

  @NotNull
  @Override
  public List<RadComponent> create() throws Exception {
    myComponents = new ArrayList<RadComponent>();

    Document document = new SAXBuilder().build(new StringReader(myXmlComponents), "UTF-8");
    for (Object element : document.getRootElement().getChildren("component")) {
      myComponents.add(createComponent((Element)element));
    }

    return myComponents;
  }

  private RadComponent createComponent(Element element) throws Exception {
    MetaModel metaModel = myMetaManager.getModelByTag(element.getAttributeValue("tag"));

    RadViewComponent component = ModelParser.createComponent(null, metaModel);
    List<RadComponent> children = component.getChildren();

    myProperties.put(component, element.getChild("properties"));

    for (Object childElement : element.getChildren("component")) {
      RadComponent childComponent = createComponent((Element)childElement);
      childComponent.setParent(component);
      children.add(childComponent);
    }

    return component;
  }

  @Override
  public void finish() throws Exception {
    for (RadComponent component : myComponents) {
      createTagAndProperties((RadViewComponent)component);
    }
  }

  private void createTagAndProperties(final RadViewComponent component) throws Exception {
    final Element properties = myProperties.get(component);
    if (properties != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          XmlTag xmlTag = component.getTag();
          if (xmlTag == null) {
            Language language = StdFileTypes.XML.getLanguage();
            xmlTag =
              XmlElementFactory.getInstance(myProject).createTagFromText("\n" + component.getMetaModel().getCreation(), language);

            RadViewComponent parent = (RadViewComponent)component.getParent();
            XmlTag parentTag = parent.getTag();

            XmlTag nextTag = null;
            List<RadComponent> children = parent.getChildren();
            int index = children.indexOf(component) + 1;
            if (index < children.size()) {
              nextTag = ((RadViewComponent)children.get(index)).getTag();
            }

            if (nextTag == null) {
              xmlTag = parentTag.addSubTag(xmlTag, false);
            }
            else {
              xmlTag = (XmlTag)parentTag.addBefore(xmlTag, nextTag);
            }
          }

          for (XmlAttribute attribute : xmlTag.getAttributes()) {
            attribute.delete();
          }

          for (Object object : properties.getAttributes()) {
            Attribute attribute = (Attribute)object;
            xmlTag.setAttribute(attribute.getName(), attribute.getValue());
          }

          for (Object object : properties.getChildren()) {
            Element element = (Element)object;
            String namespace = element.getName();

            for (Object child : element.getAttributes()) {
              Attribute attribute = (Attribute)child;
              xmlTag.setAttribute(namespace + ":" + attribute.getName(), attribute.getValue());
            }
          }
        }
      });
    }

    if (component.getProperties() == null) {
      PropertyParser propertyParser = component.getRoot().getClientProperty(PropertyParser.KEY);
      propertyParser.load(component);
    }

    for (RadComponent child : component.getChildren()) {
      createTagAndProperties((RadViewComponent)child);
    }
  }
}