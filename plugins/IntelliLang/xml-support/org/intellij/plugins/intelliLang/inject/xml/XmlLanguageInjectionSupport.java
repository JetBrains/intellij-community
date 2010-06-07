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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.XmlPatterns;
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
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static AbstractTagInjection showInjectionUI(final Project project, final BaseInjection xmlInjection) {
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

    final PsiElement element;
    AbstractTagInjection result;
    if (host instanceof XmlText) {
      final XmlTag xmlTag = ((XmlText)host).getParentTag();
      if (xmlTag == null) return null;
      element = xmlTag;
      result = new XmlTagInjection().copyFrom(injection);
    }
    else if (host instanceof XmlAttributeValue) {
      final PsiElement parent = host.getParent();
      if (!(parent instanceof XmlAttribute)) return null;
      element = parent;
      result = new XmlAttributeInjection().copyFrom(injection);
    }
    else {
      result = null;
      element = null;
    }
    final Pattern pattern = Pattern.compile("withLocalName[^\"]*\"([^\"]*)\"\\)+(?:\\.withNamespace[^\"]*\"([^\"]*)\")?");
    for (InjectionPlace place : injection.getInjectionPlaces()) {
      if (element == null || place.getElementPattern() != null && place.getElementPattern().accepts(element)) {
        final Matcher matcher = pattern.matcher(place.getText());
        if (matcher.find()) {
          final Pair<String, String> pair1 = Pair.create(matcher.group(1), matcher.group(2));
          final Pair<String, String> pair2;
          if (matcher.find(Math.max(matcher.end(1), matcher.end(2)))) {
            pair2 = Pair.create(matcher.group(1), matcher.group(2));
          }
          else pair2 = null;

          if (result == null) {
            if (place.getText().startsWith("xmlTag")) result = new XmlTagInjection().copyFrom(injection);
            else if (place.getText().startsWith("xmlAttribute")) result = new XmlAttributeInjection().copyFrom(injection);
            else continue;
          }
          if (result instanceof XmlAttributeInjection) {
            ((XmlAttributeInjection)result).setAttributeName(pair1.first);
            ((XmlAttributeInjection)result).setAttributeNamespace(StringUtil.notNullize(pair1.second));
            if (pair2 != null) {
              result.setTagName(pair2.first);
              result.setTagNamespace(StringUtil.notNullize(pair2.second));
            }
          }
          else if (result instanceof XmlTagInjection) {
            result.setTagName(pair1.first);
            result.setTagNamespace(StringUtil.notNullize(pair1.second));
          }
          else continue;
          break;
        }
      }
    }
    if (result != null) result.getInjectionPlaces().clear();
    return result;
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
          final AbstractTagInjection newInjection = showInjectionUI(project, new XmlTagInjection());
          if (newInjection != null) consumer.consume(newInjection);
        }
      },
      new AnAction("XML Attribute Injection", null, Icons.ANNOTATION_TYPE_ICON) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          final AbstractTagInjection injection = showInjectionUI(project, new XmlAttributeInjection());
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
          final AbstractTagInjection newInjection = showInjectionUI(project, injection);
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
