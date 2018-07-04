/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class RegistrationIndexer {

  private final FileContent myContent;
  private Map<String, List<RegistrationEntry>> myValueMap;

  RegistrationIndexer(FileContent content) {
    myContent = content;
  }

  @NotNull
  Map<String, List<RegistrationEntry>> indexFile() {
    final PsiFile file = myContent.getPsiFile();
    if (!(file instanceof XmlFile)) return Collections.emptyMap();

    final DomFileElement<IdeaPlugin> fileElement = DescriptorUtil.getIdeaPlugin((XmlFile)file);
    if (fileElement == null) return Collections.emptyMap();

    myValueMap = FactoryMap.create(s -> new SmartList<>());
    process(fileElement.getRootElement());

    return myValueMap;
  }

  private void process(IdeaPlugin ideaPlugin) {
    processActions(ideaPlugin);

    processComponents(ideaPlugin.getApplicationComponents(), components -> components.getComponents(),
                      RegistrationEntry.RegistrationType.APPLICATION_COMPONENT);
    processComponents(ideaPlugin.getProjectComponents(), components -> components.getComponents(),
                      RegistrationEntry.RegistrationType.PROJECT_COMPONENT);
    processComponents(ideaPlugin.getModuleComponents(), components -> components.getComponents(),
                      RegistrationEntry.RegistrationType.MODULE_COMPONENT);
  }

  private <T extends DomElement> void processComponents(List<T> componentWrappers,
                                                        Function<T, List<? extends Component>> componentGetter,
                                                        RegistrationEntry.RegistrationType type) {
    for (T wrapper : componentWrappers) {
      for (Component component : componentGetter.fun(wrapper)) {
        addEntry(component, component.getImplementationClass(), type);
      }
    }
  }

  private void processActions(IdeaPlugin ideaPlugin) {
    for (Actions actions : ideaPlugin.getActions()) {
      for (Action action : actions.getActions()) {
        processAction(action);
      }

      for (Group group : actions.getGroups()) {
        processGroup(group);
      }
    }
  }

  private void processGroup(Group group) {
    addEntry(group, group.getClazz(), RegistrationEntry.RegistrationType.ACTION);

    for (Action action : group.getActions()) {
      processAction(action);
    }
    for (Group nestedGroup : group.getGroups()) {
      processGroup(nestedGroup);
    }
  }

  private void processAction(Action action) {
    addEntry(action, action.getClazz(), RegistrationEntry.RegistrationType.ACTION);
  }

  private void addEntry(DomElement domElement,
                        GenericDomValue<PsiClass> clazzValue,
                        RegistrationEntry.RegistrationType type) {
    if (!DomUtil.hasXml(clazzValue)) return;
    final String clazz = clazzValue.getStringValue();
    if (clazz == null) return;

    final String className = clazz.replace('$', '.');

    List<RegistrationEntry> entries = myValueMap.get(className);
    RegistrationEntry entry = new RegistrationEntry(type, getOffset(domElement));
    entries.add(entry);
  }

  private static int getOffset(DomElement element) {
    final XmlElement xmlElement = element.getXmlElement();
    assert xmlElement != null : element;
    return xmlElement.getTextOffset();
  }
}
