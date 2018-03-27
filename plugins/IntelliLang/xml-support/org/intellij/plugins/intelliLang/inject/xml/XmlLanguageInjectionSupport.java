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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.EditInjectionSettingsAction;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.*;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlAttributePanel;
import org.intellij.plugins.intelliLang.inject.config.ui.XmlTagPanel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Gregory.Shrago
 */
public class XmlLanguageInjectionSupport extends AbstractLanguageInjectionSupport {

  @NonNls public static final String XML_SUPPORT_ID = "xml";

  private static boolean isMine(final PsiLanguageInjectionHost host) {
    if (host instanceof XmlAttributeValue) {
      final PsiElement p = host.getParent();
      if (p instanceof XmlAttribute) {
        final String s = ((XmlAttribute)p).getName();
        return !("xmlns".equals(s) || s.startsWith("xmlns:"));
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

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof XmlElement;
  }

  @Nullable
  @Override
  public BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref<PsiElement> commentRef) {
    if (host instanceof XmlAttributeValue) return null;
    return InjectorUtils.findCommentInjection(host instanceof XmlText ? host.getParent() : host, getId(), commentRef);
  }

  @Override
  public boolean addInjectionInPlace(Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    String id = language.getID();
    if (psiElement instanceof XmlAttributeValue) {
      return doInjectInAttributeValue((XmlAttributeValue)psiElement, id);
    }
    else if (psiElement instanceof XmlText) {
      return doInjectInXmlText((XmlText)psiElement, id);
    }
    return false;
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost host) {
    return removeInjection(host);
  }

  @Override
  public boolean removeInjection(PsiElement host) {
    final Project project = host.getProject();
    final Configuration configuration = Configuration.getProjectInstance(project);
    final ArrayList<BaseInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    final ArrayList<BaseInjection> newInjections = new ArrayList<>();
    for (BaseInjection injection : injections) {
      final BaseInjection newInjection = injection.copy();
      newInjection.setPlaceEnabled(null, false);
      if (InjectorUtils.canBeRemoved(newInjection)) continue;
      newInjections.add(newInjection);
    }
    configuration.replaceInjectionsWithUndo(
      project, newInjections, injections, Collections.emptyList());
    return true;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost host) {
    if (!isMine(host)) return false;
    final Project project = host.getProject();
    final Configuration configuration = Configuration.getProjectInstance(project);
    final ArrayList<BaseInjection> injections = collectInjections(host, configuration);
    if (injections.isEmpty()) return false;
    final BaseInjection originalInjection = injections.get(0);
    final BaseInjection xmlInjection = createFrom(originalInjection);
    final BaseInjection newInjection =
      xmlInjection == null? showDefaultInjectionUI(project, originalInjection.copy()) : showInjectionUI(project, xmlInjection);
    if (newInjection != null) {
      configuration.replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection),
        Collections.singletonList(originalInjection),
        Collections.emptyList());
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
    builder.setOkOperation(() -> {
      panel.apply();
      builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      return xmlInjection.copy();
    }
    return null;
  }

  @Nullable
  private static BaseInjection createFrom(final BaseInjection injection) {
    if (injection.getInjectionPlaces().length == 0 || injection.getInjectionPlaces().length > 1) return null;

    AbstractTagInjection result;
    final InjectionPlace place = injection.getInjectionPlaces()[0];
    final ElementPattern<? extends PsiElement> rootPattern = place.getElementPattern();
    final ElementPatternCondition<? extends PsiElement> rootCondition = rootPattern.getCondition();
    final Class<? extends PsiElement> elementClass = rootCondition.getInitialCondition().getAcceptedClass();
    if (XmlAttribute.class.equals(elementClass)) {
      result = new XmlAttributeInjection().copyFrom(injection);
    }
    else if (XmlTag.class.equals(elementClass)) {
      result = new XmlTagInjection().copyFrom(injection);
    }
    else return null;
    result.setInjectionPlaces(InjectionPlace.EMPTY_ARRAY);
    for (PatternCondition<?> condition : rootCondition.getConditions()) {
      final String value = extractValue(condition);
      if ("withLocalName".equals(condition.getDebugMethodName())) {
        if (value == null) return null;
        if (result instanceof XmlAttributeInjection) {
          ((XmlAttributeInjection)result).setAttributeName(value);
        }
        else {
          result.setTagName(value);
        }
      }
      else if ("withNamespace".equals(condition.getDebugMethodName())) {
        if (value == null) return null;
        if (result instanceof XmlAttributeInjection) {
          ((XmlAttributeInjection)result).setAttributeNamespace(value);
        }
        else {
          result.setTagNamespace(value);
        }
      }
      else if (result instanceof XmlAttributeInjection && condition instanceof PatternConditionPlus) {
        boolean strict = "withParent".equals(condition.getDebugMethodName());
        if (!strict && !"inside".equals(condition.getDebugMethodName())) return null;

        result.setApplyToSubTags(!strict);
        ElementPattern<?> insidePattern = ((PatternConditionPlus)condition).getValuePattern();
        if (!XmlTag.class.equals(insidePattern.getCondition().getInitialCondition().getAcceptedClass())) return null;
        for (PatternCondition<?> insideCondition : insidePattern.getCondition().getConditions()) {
          String tagValue = extractValue(insideCondition);
          if (tagValue == null) return null;
          if ("withLocalName".equals(insideCondition.getDebugMethodName())) {
            result.setTagName(tagValue);
          }
          else if ("withNamespace".equals(insideCondition.getDebugMethodName())) {
            result.setTagNamespace(tagValue);
          }
        }
      }
      else {
        return null;
      }
    }
    result.generatePlaces();
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
      //noinspection unchecked
      return StringUtil.join(values, "|");
    }
    return null;
  }

  public BaseInjection createInjection(Element element) {
    String place = StringUtil.notNullize(element.getChildText("place"), "");
    if (place.startsWith("xmlAttribute")) {
      return new XmlAttributeInjection();
    }
    else if (place.startsWith("xmlTag")) {
      return new XmlTagInjection();
    }
    else {
      return new BaseInjection(XML_SUPPORT_ID);
    }
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
      injection.generatePlaces();
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlTagInjection template) {
    final Configuration configuration = InjectorUtils.getEditableInstance(project);
    final AbstractTagInjection originalInjection = (AbstractTagInjection)configuration.findExistingInjection(template);

    final XmlTagInjection newInjection = originalInjection == null? template : new XmlTagInjection().copyFrom(originalInjection);
    configuration.replaceInjectionsWithUndo(
      project, Collections.singletonList(newInjection),
      ContainerUtil.createMaybeSingletonList(originalInjection),
      Collections.emptyList());
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
      injection.generatePlaces();
      doEditInjection(host.getProject(), injection);
      return true;
    }
    return false;
  }

  private static void doEditInjection(final Project project, final XmlAttributeInjection template) {
    final Configuration configuration = InjectorUtils.getEditableInstance(project);
    final BaseInjection originalInjection = configuration.findExistingInjection(template);
    final BaseInjection newInjection = originalInjection == null ? template : originalInjection.copy();
    configuration.replaceInjectionsWithUndo(
      project, Collections.singletonList(newInjection),
      ContainerUtil.createMaybeSingletonList(originalInjection),
      Collections.emptyList());
  }

  private static ArrayList<BaseInjection> collectInjections(final PsiElement host,
                                        final Configuration configuration) {
    final ArrayList<BaseInjection> result = new ArrayList<>();
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
      new AnAction("XML Tag Injection", null, PlatformIcons.XML_TAG_ICON) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          final BaseInjection newInjection = showInjectionUI(project, new XmlTagInjection());
          if (newInjection != null) consumer.consume(newInjection);
        }
      },
      new AnAction("XML Attribute Injection", null, PlatformIcons.ANNOTATION_TYPE_ICON) {
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
        final BaseInjection injection = createFrom(originalInjection);
        if (injection != null) {
          final BaseInjection newInjection = showInjectionUI(project, injection);
          if (newInjection != null) {
            originalInjection.copyFrom(newInjection);
          }
        }
        else {
          createDefaultEditAction(project, producer).actionPerformed(null);
        }
      }
    };
  }
}
