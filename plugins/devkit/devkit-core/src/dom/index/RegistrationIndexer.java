// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;

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

    processElements(ideaPlugin.getApplicationComponents(),
                    ApplicationComponents::getComponents,
                    Component::getImplementationClass,
                    RegistrationEntry.RegistrationType.APPLICATION_COMPONENT);
    processElements(ideaPlugin.getProjectComponents(),
                    ProjectComponents::getComponents,
                    Component::getImplementationClass,
                    RegistrationEntry.RegistrationType.PROJECT_COMPONENT);
    processElements(ideaPlugin.getModuleComponents(),
                    ModuleComponents::getComponents,
                    Component::getImplementationClass,
                    RegistrationEntry.RegistrationType.MODULE_COMPONENT);

    processElements(ideaPlugin.getApplicationListeners(),
                    Listeners::getListeners,
                    Listeners.Listener::getListenerClassName,
                    RegistrationEntry.RegistrationType.APPLICATION_LISTENER);
    processElements(ideaPlugin.getProjectListeners(),
                    Listeners::getListeners,
                    Listeners.Listener::getListenerClassName,
                    RegistrationEntry.RegistrationType.PROJECT_LISTENER);
  }

  private <T extends DomElement, U extends DomElement>
  void processElements(List<T> elementContainer,
                       Function<T, List<? extends U>> elementGetter,
                       Function<U, GenericDomValue<PsiClass>> psiClassGetter,
                       RegistrationEntry.RegistrationType type) {
    for (T wrapper : elementContainer) {
      for (U element : elementGetter.fun(wrapper)) {
        addEntry(element, psiClassGetter.fun(element), type);
      }
    }
  }

  private void processActions(IdeaPlugin ideaPlugin) {
    for (Actions actions : ideaPlugin.getActions()) {
      processActionContainer(actions);
    }
  }

  private void processActionContainer(ActionContainer actionContainer) {
    for (Action action : actionContainer.getActions()) {
      addEntry(action, action.getClazz(), RegistrationEntry.RegistrationType.ACTION);
      addIdEntry(action, action.getId(), RegistrationEntry.RegistrationType.ACTION_ID);
    }

    for (Group group : actionContainer.getGroups()) {
      addEntry(group, group.getClazz(), RegistrationEntry.RegistrationType.ACTION);
      addIdEntry(group, group.getId(), RegistrationEntry.RegistrationType.ACTION_GROUP_ID);

      processActionContainer(group);
    }
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
