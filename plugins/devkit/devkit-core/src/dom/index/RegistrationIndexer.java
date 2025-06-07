// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;

import java.util.List;
import java.util.Map;

class RegistrationIndexer {

  private final IdeaPlugin myPlugin;
  private final Map<String, List<RegistrationEntry>> myValueMap = FactoryMap.create(s -> new SmartList<>());

  RegistrationIndexer(IdeaPlugin plugin) {
    myPlugin = plugin;
  }

  @NotNull Map<String, List<RegistrationEntry>> indexFile() {
    process(myPlugin);

    return myValueMap;
  }

  private void process(IdeaPlugin ideaPlugin) {
    processActions(ideaPlugin);

    processComponents(RegistrationEntry.RegistrationType.APPLICATION_COMPONENT,
                      ideaPlugin.getApplicationComponents(),
                      ApplicationComponents::getComponents);

    processComponents(RegistrationEntry.RegistrationType.PROJECT_COMPONENT,
                      ideaPlugin.getProjectComponents(),
                      ProjectComponents::getComponents);

    processComponents(RegistrationEntry.RegistrationType.MODULE_COMPONENT,
                      ideaPlugin.getModuleComponents(),
                      ModuleComponents::getComponents);


    processListeners(RegistrationEntry.RegistrationType.APPLICATION_LISTENER,
                     ideaPlugin.getApplicationListeners());
    processListeners(RegistrationEntry.RegistrationType.PROJECT_LISTENER,
                     ideaPlugin.getProjectListeners());
  }

  private <T extends DomElement, U extends Component>
  void processComponents(RegistrationEntry.RegistrationType componentType,
                         List<T> componentContainer,
                         Function<T, List<? extends U>> componentGetter) {
    processElements(componentType,
                    componentContainer,
                    componentGetter,
                    Component::getImplementationClass, Component::getHeadlessImplementationClass
    );
    processElements(RegistrationEntry.RegistrationType.COMPONENT_INTERFACE,
                    componentContainer,
                    componentGetter,
                    Component::getInterfaceClass
    );
  }

  private void processListeners(@NotNull RegistrationEntry.RegistrationType listenerType,
                                @NotNull List<? extends Listeners> listenerContainer) {
    processElements(listenerType,
                    listenerContainer,
                    Listeners::getListeners,
                    Listeners.Listener::getListenerClassName
    );
    processElements(RegistrationEntry.RegistrationType.LISTENER_TOPIC,
                    listenerContainer,
                    Listeners::getListeners,
                    Listeners.Listener::getTopicClassName);
  }

  private <T extends DomElement, U extends DomElement>
  void processElements(RegistrationEntry.RegistrationType type,
                       List<T> elementContainer,
                       Function<T, List<? extends U>> elementGetter,
                       Function<U, GenericDomValue<PsiClass>>... psiClassGetters) {
    for (T wrapper : elementContainer) {
      for (U element : elementGetter.fun(wrapper)) {
        for (Function<U, GenericDomValue<PsiClass>> psiClassGetter : psiClassGetters) {
          addEntry(element, psiClassGetter.fun(element), type);
        }
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
      addIdEntry(action, action.getEffectiveId(), RegistrationEntry.RegistrationType.ACTION_ID);
    }

    for (Group group : actionContainer.getGroups()) {
      addEntry(group, group.getClazz(), RegistrationEntry.RegistrationType.ACTION);
      addIdEntry(group, group.getEffectiveId(), RegistrationEntry.RegistrationType.ACTION_GROUP_ID);

      processActionContainer(group);
    }
  }

  private void addIdEntry(DomElement domElement,
                          @Nullable String idValue,
                          RegistrationEntry.RegistrationType type) {
    if (StringUtil.isEmptyOrSpaces(idValue)) return;
    if (!DomUtil.hasXml(domElement)) return;

    storeEntry(idValue, domElement, type);
  }

  private void addEntry(DomElement domElement,
                        GenericDomValue<PsiClass> clazzValue,
                        RegistrationEntry.RegistrationType type) {
    if (!DomUtil.hasXml(clazzValue)) return;
    final String clazz = clazzValue.getStringValue();
    if (StringUtil.isEmptyOrSpaces(clazz)) return;

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
