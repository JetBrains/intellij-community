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

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Configuration that holds configured xml tag, attribute and method parameter
 * injection settings as well as the annotations to use for injection, pattern
 * validation and for substituting non-compile time constant expression.
 */
@State(
  name = Configuration.COMPONENT_NAME,
  storages = {@Storage(id = "dir", file = "$APP_CONFIG$/IntelliLang.xml", scheme = StorageScheme.DIRECTORY_BASED)})
public final class Configuration implements PersistentStateComponent<Element>, ModificationTracker {
  static final Logger LOG = Logger.getInstance(Configuration.class.getName());

  @NonNls public static final String COMPONENT_NAME = "LanguageInjectionConfiguration";

  // element names
  @NonNls private static final String TAG_INJECTION_NAME = "TAGS";
  @NonNls private static final String ATTRIBUTE_INJECTION_NAME = "ATTRIBUTES";
  @NonNls private static final String PARAMETER_INJECTION_NAME = "PARAMETERS";
  @NonNls private static final String INSTRUMENTATION_TYPE_NAME = "INSTRUMENTATION";
  @NonNls private static final String LANGUAGE_ANNOTATION_NAME = "LANGUAGE_ANNOTATION";
  @NonNls private static final String PATTERN_ANNOTATION_NAME = "PATTERN_ANNOTATION";
  @NonNls private static final String SUBST_ANNOTATION_NAME = "SUBST_ANNOTATION";
  @NonNls private static final String ENTRY_NAME = "entry";
  @NonNls private static final String RESOLVE_REFERENCES = "RESOLVE_REFERENCES";
  @NonNls private static final String USE_DFA_IF_AVAILABLE = "USE_DFA_IF_AVAILABLE";
  @NonNls private static final String INCLUDE_UNCOMPUTABLES_AS_LITERALS = "INCLUDE_UNCOMPUTABLES_AS_LITERALS";

  private final Map<String, List<BaseInjection>> myInjections = new ConcurrentFactoryMap<String, List<BaseInjection>>() {
    @Override
    protected List<BaseInjection> create(final String key) {
      return new CopyOnWriteArrayList<BaseInjection>();
    }
  };
  private ArrayList<BaseInjection> myDefaultInjections;

  // runtime pattern validation instrumentation
  @NotNull private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

  // annotation class names
  @NotNull private String myLanguageAnnotation;
  @NotNull private String myPatternAnnotation;
  @NotNull private String mySubstAnnotation;

  private boolean myResolveReferences;
  private boolean myIncludeUncomputablesAsLiterals;
  private boolean myUseDfaIfAvailable;

  // cached annotation name pairs
  private Pair<String, ? extends Set<String>> myLanguageAnnotationPair;
  private Pair<String, ? extends Set<String>> myPatternAnnotationPair;
  private Pair<String, ? extends Set<String>> mySubstAnnotationPair;

  private volatile long myModificationCount;

  public Configuration() {
    setResolveReferences(true);
    setLanguageAnnotation("org.intellij.lang.annotations.Language");
    setPatternAnnotation("org.intellij.lang.annotations.Pattern");
    setSubstAnnotation("org.intellij.lang.annotations.Subst");
  }

  public void loadState(final Element element) {
    loadState(element, true);
  }

  public void loadState(final Element element, final boolean mergeWithOriginalAndCompile) {
    final THashMap<String, LanguageInjectionSupport> supports = new THashMap<String, LanguageInjectionSupport>();
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      supports.put(support.getId(), support);
    }
    loadStateOld(element, supports.get(LanguageInjectionSupport.XML_SUPPORT_ID), supports.get(LanguageInjectionSupport.JAVA_SUPPORT_ID));
    for (Element child : (List<Element>)element.getChildren("injection")){
      final String key = child.getAttributeValue("injector-id");
      final LanguageInjectionSupport support = supports.get(key);
      final BaseInjection injection = support == null ? new BaseInjection(key) : support.createInjection(child);
      injection.loadState(child);
      myInjections.get(key).add(injection);
    }
    setInstrumentationType(JDOMExternalizerUtil.readField(element, INSTRUMENTATION_TYPE_NAME));
    setLanguageAnnotation(JDOMExternalizerUtil.readField(element, LANGUAGE_ANNOTATION_NAME));
    setPatternAnnotation(JDOMExternalizerUtil.readField(element, PATTERN_ANNOTATION_NAME));
    setSubstAnnotation(JDOMExternalizerUtil.readField(element, SUBST_ANNOTATION_NAME));
    setResolveReferences(readBoolean(element, RESOLVE_REFERENCES, true));
    setUseDfaIfAvailable(readBoolean(element, USE_DFA_IF_AVAILABLE, false));
    setIncludeUncomputablesAsLiterals(readBoolean(element, INCLUDE_UNCOMPUTABLES_AS_LITERALS, false));

    if (mergeWithOriginalAndCompile) {
      mergeWithDefaultConfiguration();

      for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
        for (BaseInjection injection : getInjections(supportId)) {
          injection.initializePlaces(true);
        }
      }
    }
  }

  private void loadStateOld(Element element, final LanguageInjectionSupport xmlSupport, final LanguageInjectionSupport javaSupport) {
    if (xmlSupport != null) {
      final Element xmlTagMarker = new Element("XmlTagInjection");
      myInjections.get(LanguageInjectionSupport.XML_SUPPORT_ID).addAll(readExternal(element.getChild(TAG_INJECTION_NAME), new Factory<BaseInjection>() {
        public BaseInjection create() {
          return xmlSupport.createInjection(xmlTagMarker);
        }
      }));
      final Element xmlAttributeMarker = new Element("XmlAttributeInjection");
      myInjections.get(LanguageInjectionSupport.XML_SUPPORT_ID).addAll(readExternal(element.getChild(ATTRIBUTE_INJECTION_NAME), new Factory<BaseInjection>() {
        public BaseInjection create() {
          return xmlSupport.createInjection(xmlAttributeMarker);
        }
      }));
    }
    if (javaSupport != null) {
      final Element javaMethodMarker = new Element("MethodParameterInjection");
      myInjections.get(LanguageInjectionSupport.JAVA_SUPPORT_ID).addAll(readExternal(element.getChild(PARAMETER_INJECTION_NAME), new Factory<BaseInjection>() {
        public BaseInjection create() {
          return javaSupport.createInjection(javaMethodMarker);
        }
      }));
    }
  }

  private static boolean readBoolean(Element element, String key, boolean defValue) {
    final String value = JDOMExternalizerUtil.readField(element, key);
    if (value == null) return defValue;
    return Boolean.parseBoolean(value);
  }

  private void mergeWithDefaultConfiguration() {
    final ArrayList<Configuration> cfgList = new ArrayList<Configuration>();
    for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
      final String config = support.getDefaultConfigUrl();
      final URL url = config == null? null : support.getClass().getResource(config);
      if (url != null) {
        try {
          cfgList.add(load(url.openStream()));
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
    }
    final THashSet<String> visitedUrls = new THashSet<String>();
    for (IdeaPluginDescriptor pluginDescriptor : PluginManager.getPlugins()) {
      final ClassLoader loader = pluginDescriptor.getPluginClassLoader();
      final URL url = loader != null ? loader.getResource("META-INF/languageInjections.xml") : null;
      if (url == null) continue;
      if (!visitedUrls.add(url.getFile())) continue; // for DEBUG mode
      try {
        cfgList.add(load(url.openStream()));
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }

    final ArrayList<BaseInjection> originalInjections = new ArrayList<BaseInjection>();
    final ArrayList<BaseInjection> newInjections = new ArrayList<BaseInjection>();
    myDefaultInjections = new ArrayList<BaseInjection>();
    for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
      for (Configuration cfg : cfgList) {
        final List<BaseInjection> imported = cfg.getInjections(supportId);
        myDefaultInjections.addAll(imported);
        importInjections(getInjections(supportId), imported, originalInjections, newInjections);
      }
    }
    replaceInjections(newInjections, originalInjections);
  }

  public Element getState() {
    final Element element = new Element(COMPONENT_NAME);

    JDOMExternalizerUtil.writeField(element, INSTRUMENTATION_TYPE_NAME, myInstrumentationType.toString());
    JDOMExternalizerUtil.writeField(element, LANGUAGE_ANNOTATION_NAME, myLanguageAnnotation);
    JDOMExternalizerUtil.writeField(element, PATTERN_ANNOTATION_NAME, myPatternAnnotation);
    JDOMExternalizerUtil.writeField(element, SUBST_ANNOTATION_NAME, mySubstAnnotation);
    JDOMExternalizerUtil.writeField(element, RESOLVE_REFERENCES, String.valueOf(myResolveReferences));

    final List<String> injectorIds = new ArrayList<String>(myInjections.keySet());
    Collections.sort(injectorIds);
    for (String key : injectorIds) {
      final List<BaseInjection> injections = new ArrayList<BaseInjection>(myInjections.get(key));
      if (myDefaultInjections != null) {
        injections.removeAll(myDefaultInjections);
      }
      Collections.sort(injections, new Comparator<BaseInjection>() {
        public int compare(final BaseInjection o1, final BaseInjection o2) {
          return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
        }
      });
      for (BaseInjection injection : injections) {
        element.addContent(injection.getState());
      }
    }
    return element;
  }

  @SuppressWarnings({"unchecked"})
  private static <T extends BaseInjection> List<T> readExternal(Element element, Factory<T> factory) {
    final List<T> injections = new ArrayList<T>();
    if (element != null) {
      final List<Element> list = element.getChildren(ENTRY_NAME);
      for (Element entry : list) {
        final T o = factory.create();
        o.loadState(entry);
        injections.add(o);
      }
    }
    return injections;
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
  public static Configuration load(final InputStream is) throws IOException, JDOMException {
    try {
      final Document document = JDOMUtil.loadDocument(is);
      final ArrayList<Element> elements = new ArrayList<Element>();
      elements.add(document.getRootElement());
      elements.addAll(document.getRootElement().getChildren("component"));
      final Element element = ContainerUtil.find(elements, new Condition<Element>() {
        public boolean value(final Element element) {
          return "component".equals(element.getName()) && COMPONENT_NAME.equals(element.getAttributeValue("name"));
        }
      });
      if (element != null) {
        final Configuration cfg = new Configuration();
        cfg.loadState(element, false);
        return cfg;
      }
      return null;
    }
    finally {
      is.close();
    }
  }

  /**
   * Import from another configuration (e.g. imported file). Returns the number of imported items.
   * @param cfg configuration to import from
   * @return added injections count
   */
  public int importFrom(final Configuration cfg) {
    final ArrayList<BaseInjection> originalInjections = new ArrayList<BaseInjection>();
    final ArrayList<BaseInjection> newInjections = new ArrayList<BaseInjection>();
    for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
      importInjections(getInjections(supportId), cfg.getInjections(supportId), originalInjections, newInjections);
    }
    if (!newInjections.isEmpty()) configurationModified();
    replaceInjections(newInjections, originalInjections);
    return newInjections.size();
  }

  static void importInjections(final Collection<BaseInjection> existingInjections, final Collection<BaseInjection> importingInjections,
                               final Collection<BaseInjection> originalInjections, final Collection<BaseInjection> newInjections) {
    final MultiValuesMap<String, BaseInjection> existingMap = createInjectionMap(existingInjections);
    main: for (BaseInjection other : importingInjections) {
      final List<BaseInjection> matchingInjections = ContainerUtil.concat(other.getInjectionPlaces(), new Function<InjectionPlace, Collection<? extends BaseInjection>>() {
        public Collection<? extends BaseInjection> fun(final InjectionPlace o) {
          final Collection<BaseInjection> collection = existingMap.get(o.getText());
          return collection == null? Collections.<BaseInjection>emptyList() : collection;
        }
      });
      if (matchingInjections.isEmpty()) {
        newInjections.add(other);
      }
      else {
        BaseInjection existing = null;
        for (BaseInjection injection : matchingInjections) {
          if (injection.equals(other)) continue main;
          if (existing == null && injection.sameLanguageParameters(other)) {
            existing = injection;
          }
        }
        if (existing == null) continue main; // skip!! language changed
        final BaseInjection newInjection = existing.copy();
        newInjection.mergeOriginalPlacesFrom(other, true);
        if (!newInjection.equals(existing)) {
          originalInjections.add(existing);
          newInjections.add(newInjection);
        }
      }
    }
  }

  private static MultiValuesMap<String, BaseInjection> createInjectionMap(final Collection<BaseInjection> injections) {
    final MultiValuesMap<String, BaseInjection> existingMap = new MultiValuesMap<String, BaseInjection>();
    for (BaseInjection injection : injections) {
      for (InjectionPlace place : injection.getInjectionPlaces()) {
        existingMap.put(place.getText(), injection);
      }
    }
    return existingMap;
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

  public boolean isIncludeUncomputablesAsLiterals() {
    return myIncludeUncomputablesAsLiterals;
  }

  public void setIncludeUncomputablesAsLiterals(boolean flag) {
    myIncludeUncomputablesAsLiterals = flag;
  }

  public boolean isUseDfaIfAvailable() {
    return myUseDfaIfAvailable;
  }

  public void setUseDfaIfAvailable(boolean flag) {
    myUseDfaIfAvailable = flag;
  }

  @Nullable
  public BaseInjection findExistingInjection(@NotNull final BaseInjection injection) {
    final List<BaseInjection> list = getInjections(injection.getSupportId());
    for (BaseInjection cur : list) {
      if (cur.intersectsWith(injection)) return cur;
    }
    return null;
  }

  public boolean setHostInjectionEnabled(final PsiLanguageInjectionHost host, final Collection<String> languages, final boolean enabled) {
    final ArrayList<BaseInjection> originalInjections = new ArrayList<BaseInjection>();
    final ArrayList<BaseInjection> newInjections = new ArrayList<BaseInjection>();
    for (String supportId : getAllInjectorIds()) {
      for (BaseInjection injection : getInjections(supportId)) {
        if (!languages.contains(injection.getInjectedLanguageId())) continue;
        boolean replace = false;
        final ArrayList<InjectionPlace> newPlaces = new ArrayList<InjectionPlace>();
        for (InjectionPlace place : injection.getInjectionPlaces()) {
          if (place.isEnabled() != enabled && place.getElementPattern() != null &&
              (place.getElementPattern().accepts(host) || place.getElementPattern().accepts(host.getParent()))) {
            newPlaces.add(new InjectionPlace(place.getText(), place.getElementPattern(), enabled));
            replace = true;
          }
          else newPlaces.add(place);
        }
        if (replace) {
          originalInjections.add(injection);
          final BaseInjection newInjection = injection.copy();
          newInjection.getInjectionPlaces().clear();
          newInjection.getInjectionPlaces().addAll(newPlaces);
          newInjections.add(newInjection);
        }
      }
    }
    if (!originalInjections.isEmpty()) {
      replaceInjectionsWithUndo(host.getProject(), newInjections, originalInjections, Collections.<PsiElement>emptyList());
      return true;
    }
    return false;
  }

  public enum InstrumentationType {
    NONE, ASSERT, EXCEPTION
  }

  public InstrumentationType getInstrumentation() {
    return myInstrumentationType;
  }

  @NotNull
  public List<BaseInjection> getInjections(final String injectorId) {
    return Collections.unmodifiableList(myInjections.get(injectorId));
  }

  public Set<String> getAllInjectorIds() {
    return Collections.unmodifiableSet(myInjections.keySet());
  }

  public void replaceInjectionsWithUndo(final Project project,
                                final List<? extends BaseInjection> newInjections,
                                final List<? extends BaseInjection> originalInjections,
                                final List<? extends PsiElement> psiElementsToRemove) {
    replaceInjectionsWithUndo(project, newInjections, originalInjections, psiElementsToRemove,
                      new PairProcessor<List<? extends BaseInjection>, List<? extends BaseInjection>>() {
      public boolean process(final List<? extends BaseInjection> add, final List<? extends BaseInjection> remove) {
        replaceInjections(add, remove);
        return true;
      }
    });
  }

  public static <T> void replaceInjectionsWithUndo(final Project project, final T add, final T remove,
                                final List<? extends PsiElement> psiElementsToRemove,
                                final PairProcessor<T, T> actualProcessor) {
    final UndoableAction action = new UndoableAction() {
      public void undo() {
        actualProcessor.process(remove, add);
      }

      public void redo() {
        actualProcessor.process(add, remove);
      }

      public DocumentReference[] getAffectedDocuments() {
        return DocumentReference.EMPTY_ARRAY;
      }

      public boolean isGlobal() {
        return true;
      }
    };
    final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(psiElementsToRemove, new NullableFunction<PsiElement, PsiFile>() {
      public PsiFile fun(final PsiElement psiAnnotation) {
        return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
      }
    });
    new WriteCommandAction.Simple(project, "Language Injection Configuration Update", psiFiles.toArray(new PsiFile[psiFiles.size()])) {
      public void run() {
        for (PsiElement annotation : psiElementsToRemove) {
          annotation.delete();
        }
        actualProcessor.process(add, remove);
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }.execute();
  }

  public void replaceInjections(final List<? extends BaseInjection> newInjections, final List<? extends BaseInjection> originalInjections) {
    for (BaseInjection injection : originalInjections) {
      myInjections.get(injection.getSupportId()).remove(injection);
    }
    for (BaseInjection injection : newInjections) {
      injection.initializePlaces(true);
      myInjections.get(injection.getSupportId()).add(injection);
    }
    configurationModified();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }


}
