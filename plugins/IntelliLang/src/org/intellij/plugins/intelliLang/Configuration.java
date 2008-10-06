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
package org.intellij.plugins.intelliLang;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Configuration that holds configured xml tag, attribute and method parameter
 * injection settings as well as the annotations to use for injection, pattern
 * validation and for substituting non-compile time constant expression.
 */
@State(
    name = Configuration.COMPONENT_NAME,
    storages = {
      @Storage(id = "dir", file = "$APP_CONFIG$/IntelliLang.xml", scheme = StorageScheme.DIRECTORY_BASED)
        })
public final class Configuration implements PersistentStateComponent<Element> {

  @NonNls
  static final String COMPONENT_NAME = "LanguageInjectionConfiguration";

  // element names

  @NonNls
  private static final String TAG_INJECTION_NAME = "TAGS";
  @NonNls
  private static final String ATTRIBUTE_INJECTION_NAME = "ATTRIBUTES";
  @NonNls
  private static final String PARAMETER_INJECTION_NAME = "PARAMETERS";
  @NonNls
  private static final String INSTRUMENTATION_TYPE_NAME = "INSTRUMENTATION";
  @NonNls
  private static final String LANGUAGE_ANNOTATION_NAME = "LANGUAGE_ANNOTATION";
  @NonNls
  private static final String PATTERN_ANNOTATION_NAME = "PATTERN_ANNOTATION";
  @NonNls
  private static final String SUBST_ANNOTATION_NAME = "SUBST_ANNOTATION";
  @NonNls
  private static final String ENTRY_NAME = "entry";
  @NonNls
  private static final String RESOLVE_REFERENCES = "RESOLVE_REFERENCES";

  // injection configuration

  private final List<XmlTagInjection> myTagInjections = new ArrayList<XmlTagInjection>();
  private final List<XmlAttributeInjection> myAttributeInjections = new ArrayList<XmlAttributeInjection>();
  private final List<MethodParameterInjection> myParameterInjections = new ArrayList<MethodParameterInjection>();

  // runtime pattern validation instrumentation
  @NotNull
  private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

  // annotation class names

  @NotNull
  private String myLanguageAnnotation;
  @NotNull
  private String myPatternAnnotation;
  @NotNull
  private String mySubstAnnotation;

  private boolean myResolveReferences;

  // cached annotation name pairs

  private Pair<String, ? extends Set<String>> myLanguageAnnotationPair;
  private Pair<String, ? extends Set<String>> myPatternAnnotationPair;
  private Pair<String, ? extends Set<String>> mySubstAnnotationPair;

  private volatile long myModificationCount;

  Configuration() {
    setLanguageAnnotation("org.intellij.lang.annotations.Language");
    setPatternAnnotation("org.intellij.lang.annotations.Pattern");
    setSubstAnnotation("org.intellij.lang.annotations.Subst");
  }

  public void loadState(final Element element) {
    readExternal(myTagInjections, element.getChild(TAG_INJECTION_NAME), new Factory<XmlTagInjection>() {
      public XmlTagInjection create() {
        return new XmlTagInjection();
      }
    });
    readExternal(myAttributeInjections, element.getChild(ATTRIBUTE_INJECTION_NAME), new Factory<XmlAttributeInjection>() {
      public XmlAttributeInjection create() {
        return new XmlAttributeInjection();
      }
    });
    readExternal(myParameterInjections, element.getChild(PARAMETER_INJECTION_NAME), new Factory<MethodParameterInjection>() {
      public MethodParameterInjection create() {
        return new MethodParameterInjection();
      }
    });

    setInstrumentationType(JDOMExternalizerUtil.readField(element, INSTRUMENTATION_TYPE_NAME));
    setLanguageAnnotation(JDOMExternalizerUtil.readField(element, LANGUAGE_ANNOTATION_NAME));
    setPatternAnnotation(JDOMExternalizerUtil.readField(element, PATTERN_ANNOTATION_NAME));
    setSubstAnnotation(JDOMExternalizerUtil.readField(element, SUBST_ANNOTATION_NAME));
    final String resolveReferences = JDOMExternalizerUtil.readField(element, RESOLVE_REFERENCES);
    setResolveReferences(resolveReferences == null || Boolean.parseBoolean(resolveReferences));
  }

  public Element getState() {
    final Element element = new Element(COMPONENT_NAME);
    writeExternal(element, TAG_INJECTION_NAME, myTagInjections);
    writeExternal(element, ATTRIBUTE_INJECTION_NAME, myAttributeInjections);
    writeExternal(element, PARAMETER_INJECTION_NAME, myParameterInjections);

    JDOMExternalizerUtil.writeField(element, INSTRUMENTATION_TYPE_NAME, myInstrumentationType.toString());
    JDOMExternalizerUtil.writeField(element, LANGUAGE_ANNOTATION_NAME, myLanguageAnnotation);
    JDOMExternalizerUtil.writeField(element, PATTERN_ANNOTATION_NAME, myPatternAnnotation);
    JDOMExternalizerUtil.writeField(element, SUBST_ANNOTATION_NAME, mySubstAnnotation);
    JDOMExternalizerUtil.writeField(element, RESOLVE_REFERENCES, String.valueOf(myResolveReferences));
    return element;
  }

  @SuppressWarnings({"unchecked"})
  private static <T extends BaseInjection> void readExternal(List<T> injections, Element element, Factory<T> factory)
      {
    injections.clear();

    if (element != null) {
      final List<Element> list = element.getChildren(ENTRY_NAME);
      for (Element entry : list) {
        final T o = factory.create();
        o.loadState(entry);
        injections.add(o);
      }
    }
  }

  private static <T extends BaseInjection> void writeExternal(Element parent, String name, List<T> injections) {
    final Element element = new Element(name);
    parent.addContent(element);

    for (T injection : injections) {
      final Element entry = new Element(ENTRY_NAME);
      element.addContent(entry);
      entry.addContent(injection.getState());
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myTagInjections.clear();
    myAttributeInjections.clear();
    myParameterInjections.clear();
  }

  public List<XmlTagInjection> getTagInjections() {
    return myTagInjections;
  }

  public List<XmlAttributeInjection> getAttributeInjections() {
    return myAttributeInjections;
  }

  public List<MethodParameterInjection> getParameterInjections() {
    return myParameterInjections;
  }

  public static Configuration getInstance() {
    return ServiceManager.getService(Configuration.class);
  }

  public String getLanguageAnnotationClass() {
    return myLanguageAnnotation;
  }

  public String getPatternAnnotationClass() {
    return myPatternAnnotation;
  }

  public String getSubstAnnotationClass() {
    return mySubstAnnotation;
  }

  public void setInstrumentationType(@Nullable String type) {
    if (type != null) {
      setInstrumentationType(InstrumentationType.valueOf(type));
    }
  }

  public void setInstrumentationType(@NotNull InstrumentationType type) {
    myInstrumentationType = type;
  }

  public void setLanguageAnnotation(@Nullable String languageAnnotation) {
    if (languageAnnotation == null) return;
    myLanguageAnnotation = languageAnnotation;
    myLanguageAnnotationPair = Pair.create(languageAnnotation, Collections.singleton(languageAnnotation));
  }

  public Pair<String, ? extends Set<String>> getLanguageAnnotationPair() {
    return myLanguageAnnotationPair;
  }

  public void setPatternAnnotation(@Nullable String patternAnnotation) {
    if (patternAnnotation == null) return;
    myPatternAnnotation = patternAnnotation;
    myPatternAnnotationPair = Pair.create(patternAnnotation, Collections.singleton(patternAnnotation));
  }

  public Pair<String, ? extends Set<String>> getPatternAnnotationPair() {
    return myPatternAnnotationPair;
  }

  public void setSubstAnnotation(@Nullable String substAnnotation) {
    if (substAnnotation == null) return;
    mySubstAnnotation = substAnnotation;
    mySubstAnnotationPair = Pair.create(substAnnotation, Collections.singleton(substAnnotation));
  }

  public Pair<String, ? extends Set<String>> getSubstAnnotationPair() {
    return mySubstAnnotationPair;
  }

  @Nullable
  public static Configuration load(VirtualFile file) throws IOException, JDOMException, InvalidDataException {
    final InputStream is = file.getInputStream();
    final Document document = JDOMUtil.loadDocument(is);

    final String name = "'" + COMPONENT_NAME + "'";

    //noinspection unchecked
    final List<Element> list = XPath.selectNodes(document, "//component[@name=" + name + "]");

    if (list.size() == 1) {
      final Configuration cfg = new Configuration();
      cfg.loadState(list.get(0));
      return cfg;
    }
    return null;
  }

  /**
   * Import from another configuration (e.g. imported file). Returns the number of imported items.
   */
  public int importFrom(Configuration cfg) {
    int n = 0;
    for (XmlTagInjection injection : cfg.myTagInjections) {
      if (!myTagInjections.contains(injection)) {
        myTagInjections.add(injection);
        n++;
      }
    }
    for (XmlAttributeInjection injection : cfg.myAttributeInjections) {
      if (!myAttributeInjections.contains(injection)) {
        myAttributeInjections.add(injection);
        n++;
      }
    }
    for (MethodParameterInjection injection : cfg.myParameterInjections) {
      if (!myParameterInjections.contains(injection)) {
        myParameterInjections.add(injection);
        n++;
      }
    }
    if (n < 0) configurationModified();
    return n;
  }

  public void configurationModified() {
    myModificationCount ++;
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public boolean isResolveReferences() {
    return myResolveReferences;
  }

  public void setResolveReferences(final boolean resolveReferences) {
    myResolveReferences = resolveReferences;
  }

  @Nullable
  public <T extends BaseInjection> T findExistingInjection(final T injection) {
    if (injection instanceof XmlAttributeInjection) {
      final XmlAttributeInjection template = (XmlAttributeInjection)injection;
      for (XmlAttributeInjection cur : myAttributeInjections) {
        if (Comparing.equal(cur.getInjectedLanguageId(), template.getInjectedLanguageId()) &&
            Comparing.equal(cur.getAttributeName(), template.getAttributeName()) &&
            Comparing.equal(cur.getAttributeNamespace(), template.getAttributeNamespace()) &&
            Comparing.equal(cur.getTagName(), template.getTagName()) &&
            Comparing.equal(cur.getTagNamespace(), template.getTagNamespace())) {
          return (T)cur;
        }
      }
    }
    else if (injection instanceof XmlTagInjection) {
      final XmlTagInjection template = (XmlTagInjection)injection;
      for (XmlTagInjection cur : myTagInjections) {
        if (Comparing.equal(cur.getInjectedLanguageId(), template.getInjectedLanguageId()) &&
            Comparing.equal(cur.getTagName(), template.getTagName()) &&
            Comparing.equal(cur.getTagNamespace(), template.getTagNamespace())) {
          return (T)cur;
        }
      }
    }
    else if (injection instanceof MethodParameterInjection) {
      final MethodParameterInjection template = (MethodParameterInjection)injection;
      for (MethodParameterInjection cur : myParameterInjections) {
        if (Comparing.equal(cur.getInjectedLanguageId(), template.getInjectedLanguageId()) &&
            Comparing.equal(cur.getClassName(), template.getClassName())) {
          return (T)cur;
        }
      }
    }
    return null;
  }

  public enum InstrumentationType {
    NONE, ASSERT, EXCEPTION
  }

  public InstrumentationType getInstrumentation() {
    return myInstrumentationType;
  }
}
