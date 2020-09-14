// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.StatisticsCollectorType;

import java.util.List;
import java.util.Map;

class RegistrationIndexer {

  private final IdeaPlugin myPlugin;
  private final Map<String, List<RegistrationEntry>> myValueMap = FactoryMap.create(s -> new SmartList<>());

  RegistrationIndexer(IdeaPlugin plugin) {
    myPlugin = plugin;
  }

  @NotNull
  Map<String, List<RegistrationEntry>> indexFile() {
    process(myPlugin);

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

    processStatisticsCollectors(ideaPlugin);
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

  private void processStatisticsCollectors(@NotNull IdeaPlugin ideaPlugin) {
    for (Extensions extensions : ideaPlugin.getExtensions()) {
      if (!Extensions.DEFAULT_PREFIX.equals(extensions.getDefaultExtensionNs().getStringValue())) continue;
      for (XmlTag tag : extensions.getXmlTag().getSubTags()) {
        String extensionName = tag.getLocalName();
        StatisticsCollectorType collectorType = StatisticsCollectorType.findByExtensionPoint(extensionName);
        if (collectorType == null) continue;
        XmlAttribute implementationAttribute = tag.getAttribute(collectorType.getImplementationAttribute());
        if (implementationAttribute == null) continue;
        storeEntry(implementationAttribute.getDisplayValue(), extensions, RegistrationEntry.RegistrationType.STATISTICS_COLLECTOR);
      }
    }
  }

  private void processGroup(Group group) {
    addEntry(group, group.getClazz(), RegistrationEntry.RegistrationType.ACTION);
    addIdEntry(group, group.getId(), RegistrationEntry.RegistrationType.ACTION_GROUP_ID);

    for (Action action : group.getActions()) {
      processAction(action);
    }
    for (Group nestedGroup : group.getGroups()) {
      processGroup(nestedGroup);
    }
  }

  private void processAction(Action action) {
    addEntry(action, action.getClazz(), RegistrationEntry.RegistrationType.ACTION);
    addIdEntry(action, action.getId(), RegistrationEntry.RegistrationType.ACTION_ID);
  }

  private void addIdEntry(DomElement domElement,
                          GenericAttributeValue<String> idValue,
                          RegistrationEntry.RegistrationType type) {
    if (!DomUtil.hasXml(domElement)) return;
    String id = idValue.getStringValue();
    if (StringUtil.isEmpty(id)) return;

    storeEntry(id, domElement, type);
  }

  private void addEntry(DomElement domElement,
                        GenericDomValue<PsiClass> clazzValue,
                        RegistrationEntry.RegistrationType type) {
    if (!DomUtil.hasXml(clazzValue)) return;
    final String clazz = clazzValue.getStringValue();
    if (clazz == null) return;

    final String className = clazz.replace('$', '.');

    storeEntry(className, domElement, type);
  }

  private void storeEntry(String key, DomElement domElement, RegistrationEntry.RegistrationType type) {
    List<RegistrationEntry> entries = myValueMap.get(key);

    final XmlElement xmlElement = domElement.getXmlElement();
    assert xmlElement != null : domElement;
    RegistrationEntry entry = new RegistrationEntry(type, xmlElement.getTextOffset());
    entries.add(entry);
  }
}
