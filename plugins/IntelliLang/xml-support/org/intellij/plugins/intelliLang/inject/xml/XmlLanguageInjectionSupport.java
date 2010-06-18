/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang.inject.xml;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.EditInjectionSettingsAction;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.config.*;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlAttributePanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlTagPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlAttributeInjectionConfigurable;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlTagInjectionConfigurable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
public class XmlLanguageInjectionSupport extends AbstractLanguageInjectionSupport {

  private static boolean isMine(final PsiLanguageInjectionHost host) {
    if (host instanceof XmlAttributeValue) {
      final PsiElement p = host.getParent();
      if (p instanceof XmlAttribute) {
        final String s = ((XmlAttribute)p).getName();
        return !(s.equals("xmlns") || s.startsWith("xmlns:"));
      }
    }
    else if (host instanceof XmlText) {
      final XmlTag tag = ((XmlText)host).getParentTag();
      return tag != null/* && tag.getValue().getTextElements().length == 1 && tag.getSubTags().length == 0*/;
    }
    return false;
  }

  @NotNull
  public String getId() {
    return XML_SUPPORT_ID;
  }

  @NotNull
  public Class[] getPatternClasses() {
    return new Class[] {XmlPatterns.class};
  }

  public boolean useDefaultInjector(final PsiElement host) {
    return false;
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    if (psiElement instanceof XmlAttributeValue) {
      return doInjectInAttributeValue((XmlAttributeValue)psiElement, language.getID());
    }
    else if (psiElement instanceof XmlText) {
      return doInjectInXmlText((XmlText)psiElement, language.getID());
    }
    return false;
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost host) {
    if (!isMine(host)) return false;
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<BaseInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    final ArrayList<BaseInjection> newInjections = new ArrayList<BaseInjection>();
    for (BaseInjection injection : injections) {
      final BaseInjection newInjection = injection.copy();
      newInjection.setPlaceEnabled(null, false);
    }
    Configuration.getInstance().replaceInjectionsWithUndo(
      host.getProject(), newInjections, injections, Collections.<PsiElement>emptyList());
    return true;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost host) {
    if (!isMine(host)) return false;
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<BaseInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    final Project project = host.getProject();
    final BaseInjection originalInjection = injections.get(0);
    final BaseInjection xmlInjection = createFrom(originalInjection, host);
    final BaseInjection newInjection =
      xmlInjection == null? showDefaultInjectionUI(project, originalInjection.copy()) : showInjectionUI(project, xmlInjection);
    if (newInjection != null) {
      Configuration.getInstance().replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection),
        Collections.singletonList(originalInjection),
        Collections.<PsiElement>emptyList());
    }
    return true;
  }

  @Nullable
  private static BaseInjection showInjectionUI(final Project project, final BaseInjection xmlInjection) {
    final DialogBuilder builder = new DialogBuilder(project);
    final AbstractInjectionPanel panel;
    if (xmlInjection instanceof XmlTagInjection) {
      panel = new XmlTagPanel((XmlTagInjection)xmlInjection, project);
      builder.setHelpId("reference.settings.injection.language.injection.settings.xml.tag");
    }
    else if (xmlInjection instanceof XmlAttributeInjection) {
      panel = new XmlAttributePanel((XmlAttributeInjection)xmlInjection, project);
      builder.setHelpId("reference.settings.injection.language.injection.settings.xml.attribute");
    }
    else throw new AssertionError();
    panel.reset();
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(new Runnable() {
      public void run() {
        panel.apply();
        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
      }
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      xmlInjection.initializePlaces(false);
      return new AbstractTagInjection().copyFrom(xmlInjection);
    }
    return null;
  }

  @Nullable
  private static BaseInjection createFrom(final BaseInjection injection, final PsiLanguageInjectionHost host) {
    if (injection.getInjectionPlaces().size() > 1) return null;

    AbstractTagInjection result;
    final InjectionPlace place = injection.getInjectionPlaces().get(0);
    final ElementPattern<PsiElement> rootPattern = place.getElementPattern();
    final ElementPatternCondition<PsiElement> rootCondition = rootPattern.getCondition();
    final Class<PsiElement> elementClass = rootCondition.getInitialCondition().getAcceptedClass();
    if (XmlAttribute.class.equals(elementClass)) {
      result = new XmlAttributeInjection().copyFrom(injection);
    }
    else if (XmlTag.class.equals(elementClass)) {
      result = new XmlTagInjection().copyFrom(injection);
    }
    else return null;
    result.getInjectionPlaces().clear();
    for (PatternCondition<? super PsiElement> condition : rootCondition.getConditions()) {
      final String value = extractValue(condition);
      if (condition.getDebugMethodName().equals("withLocalName")) {
        if (value == null) return null;
        if (result instanceof XmlAttributeInjection) {
          ((XmlAttributeInjection)result).setAttributeName(value);
        }
        else {
          result.setTagName(value);
        }
      }
      else if (condition.getDebugMethodName().equals("withNamespace")) {
        if (value == null) return null;
        if (result instanceof XmlAttributeInjection) {
          ((XmlAttributeInjection)result).setAttributeNamespace(value);
        }
        else {
          result.setTagNamespace(value);
        }
      }
      else if (result instanceof XmlAttributeInjection && condition.getDebugMethodName().equals("inside") && condition instanceof PatternConditionPlus) {
        final ElementPattern<?> insidePattern = ((PatternConditionPlus)condition).getValuePattern();
        if (!XmlTag.class.equals(insidePattern.getCondition().getInitialCondition().getAcceptedClass())) return null;
        for (PatternCondition<?> insideCondition : insidePattern.getCondition().getConditions()) {
          final String tagValue = extractValue(insideCondition);
          if (tagValue == null) return null;
          if (insideCondition.getDebugMethodName().equals("withLocalName")) {
            result.setTagName(tagValue);
          }
          else if (insideCondition.getDebugMethodName().equals("withNamespace")) {
            result.setTagNamespace(tagValue);
          }

        }
      }
      else return null;
    }
    return result;
  }

  @Nullable
  private static String extractValue(PatternCondition<?> condition) {
    if (!(condition instanceof PatternConditionPlus)) return null;
    final ElementPattern valuePattern = ((PatternConditionPlus)condition).getValuePattern();
    final ElementPatternCondition<?> rootCondition = valuePattern.getCondition();
    if (!String.class.equals(rootCondition.getInitialCondition().getAcceptedClass())) return null;
    if (rootCondition.getConditions().size() != 1) return null;
    final PatternCondition<?> valueCondition = rootCondition.getConditions().get(0);
    if (!(valueCondition instanceof ValuePatternCondition<?>)) return null;
    final Collection values = ((ValuePatternCondition)valueCondition).getValues();
    if (values.size() == 1) {
      final Object value = values.iterator().next();
      return value instanceof String? (String)value : null;
    }
    else if (!values.isEmpty()) {
      for (Object value : values) {
        if (!(value instanceof String)) return null;
      }
      return StringUtil.join(values, "|");
    }
    return null;
  }

  public BaseInjection createInjection(final Element element) {
    if (element.getName().equals(XmlAttributeInjection.class.getSimpleName())) {
      return new XmlAttributeInjection();
    }
    else if (element.getName().equals(XmlTagInjection.class.getSimpleName())) {
      return new XmlTagInjection();
    }
    return new AbstractTagInjection();
  }

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[0];
  }

  private static boolean doInjectInXmlText(final XmlText host, final String languageId) {
    final XmlTag tag = host.getParentTag();
    if (tag != null) {
      final XmlTagInjection injection = new XmlTagInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setTagName(tag.getLocalName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlTagInjection template) {
    final AbstractTagInjection originalInjection = (AbstractTagInjection)Configuration.getInstance().findExistingInjection(template);

    final XmlTagInjection newInjection = originalInjection == null? template : new XmlTagInjection().copyFrom(originalInjection);
    if (InjectLanguageAction.doEditConfigurable(project, new XmlTagInjectionConfigurable(newInjection, null, project))) {
      Configuration.getInstance().replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection),
        ContainerUtil.createMaybeSingletonList(originalInjection),
        Collections.<PsiElement>emptyList());
    }
  }

  private static boolean doInjectInAttributeValue(final XmlAttributeValue host, final String languageId) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(host, XmlAttribute.class, true);
    final XmlTag tag = attribute == null? null : attribute.getParent();
    if (tag != null) {
      final XmlAttributeInjection injection = new XmlAttributeInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setAttributeName(attribute.getLocalName());
      injection.setAttributeNamespace(attribute.getNamespace());
      injection.setTagName(tag.getLocalName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlAttributeInjection template) {
    final Configuration configuration = Configuration.getInstance();
    template.initializePlaces(false);
    final BaseInjection originalInjection = configuration.findExistingInjection(template);
    final BaseInjection newInjection = originalInjection == null ? template : originalInjection.copy();
    if (InjectLanguageAction.doEditConfigurable(project, new XmlAttributeInjectionConfigurable((XmlAttributeInjection)newInjection, null, project))) {
      Configuration.getInstance().replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection),
        ContainerUtil.createMaybeSingletonList(originalInjection),
        Collections.<PsiElement>emptyList());
    }
  }

  private static ArrayList<BaseInjection> collectInjections(final PsiLanguageInjectionHost host,
                                        final Configuration configuration) {
    final ArrayList<BaseInjection> result = new ArrayList<BaseInjection>();
    final PsiElement element = host instanceof XmlText? ((XmlText)host).getParentTag() :
                               host instanceof XmlAttributeValue? host.getParent(): host;
    for (BaseInjection injection : configuration.getInjections(XML_SUPPORT_ID)) {
      if (injection.acceptsPsiElement(element)) {
        result.add(injection);
      }
    }
    return result;
  }

  @Override
  public AnAction[] createAddActions(final Project project, final Consumer<BaseInjection> consumer) {
    return new AnAction[] {
      new AnAction("XML Tag Injection", null, Icons.XML_TAG_ICON) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          final BaseInjection newInjection = showInjectionUI(project, new XmlTagInjection());
          if (newInjection != null) consumer.consume(newInjection);
        }
      },
      new AnAction("XML Attribute Injection", null, Icons.ANNOTATION_TYPE_ICON) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          final BaseInjection injection = showInjectionUI(project, new XmlAttributeInjection());
          if (injection != null) consumer.consume(injection);
        }
      }
    };
  }

  @Override
  public AnAction createEditAction(final Project project, final Factory<BaseInjection> producer) {
    return new AnAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        final BaseInjection originalInjection = producer.create();
        final BaseInjection injection = createFrom(originalInjection, null);
        if (injection != null) {
          final BaseInjection newInjection = showInjectionUI(project, injection);
          if (newInjection != null) {
            originalInjection.copyFrom(newInjection);
            originalInjection.initializePlaces(true);
          }
        }
        else {
          createDefaultEditAction(project, producer).actionPerformed(null);
        }
      }
    };
  }
}
