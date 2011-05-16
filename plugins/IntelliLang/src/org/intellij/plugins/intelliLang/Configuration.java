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
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
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

/**
 * Configuration that holds configured xml tag, attribute and method parameter
 * injection settings as well as the annotations to use for injection, pattern
 * validation and for substituting non-compile time constant expression.
 *
 * Making it a service may result in FileContentUtil.reparseFiles at a random loading moment which may cause
 * mysterious PSI validity losses
 */
public class Configuration implements PersistentStateComponent<Element>, ModificationTracker {

  static final Logger LOG = Logger.getInstance(Configuration.class.getName());

  @State(
    name = Configuration.COMPONENT_NAME,
    storages = {@Storage(id = "dir", file = "$APP_CONFIG$/IntelliLang.xml", scheme = StorageScheme.DIRECTORY_BASED)})
  public static class App extends Configuration {

    private final List<BaseInjection> myDefaultInjections;
    private final AdvancedConfiguration myAdvancedConfiguration;

    App() {
      myDefaultInjections = loadDefaultInjections();
      myAdvancedConfiguration = new AdvancedConfiguration();
    }

    @Override
    public List<BaseInjection> getDefaultInjections() {
      return myDefaultInjections;
    }

    @Override
    public AdvancedConfiguration getAdvancedConfiguration() {
      return myAdvancedConfiguration;
    }

    @Override
    public void loadState(final Element element) {
      myAdvancedConfiguration.loadState(element);
      super.loadState(element);
    }

    @Override
    public Element getState() {
      final Element element = new Element(COMPONENT_NAME);
      myAdvancedConfiguration.writeState(element);
      return getState(element);
    }
  }
  @State(
    name = Configuration.COMPONENT_NAME,
    storages = {@Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/IntelliLang.xml", scheme = StorageScheme.DIRECTORY_BASED)})
  public static class Prj extends Configuration {

    private final Configuration myParentConfiguration;

    Prj(final Configuration configuration) {
      myParentConfiguration = configuration;
    }

    @Override
    public AdvancedConfiguration getAdvancedConfiguration() {
      return myParentConfiguration.getAdvancedConfiguration();
    }

    @Override
    public List<BaseInjection> getDefaultInjections() {
      return myParentConfiguration.getDefaultInjections();
    }

    @NotNull
    @Override
    public List<BaseInjection> getInjections(final String injectorId) {
      return ContainerUtil.concat(myParentConfiguration.getInjections(injectorId), getOwnInjections(injectorId));
    }

    public Configuration getParentConfiguration() {
      return myParentConfiguration;
    }

    public List<BaseInjection> getOwnInjections(final String injectorId) {
      return super.getInjections(injectorId);
    }

    @Override
    public long getModificationCount() {
      return super.getModificationCount() + myParentConfiguration.getModificationCount();
    }
  }

  public enum InstrumentationType {
    NONE, ASSERT, EXCEPTION
  }

  public enum DfaOption {
    OFF, RESOLVE, ASSIGNMENTS, DFA
  }

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
  @NonNls private static final String LOOK_FOR_VAR_ASSIGNMENTS = "LOOK_FOR_VAR_ASSIGNMENTS";
  @NonNls private static final String USE_DFA_IF_AVAILABLE = "USE_DFA_IF_AVAILABLE";
  @NonNls private static final String INCLUDE_UNCOMPUTABLES_AS_LITERALS = "INCLUDE_UNCOMPUTABLES_AS_LITERALS";

  private final Map<String, List<BaseInjection>> myInjections = new ConcurrentFactoryMap<String, List<BaseInjection>>() {
    @Override
    protected List<BaseInjection> create(final String key) {
      return ContainerUtil.createEmptyCOWList();
    }
  };

  private volatile long myModificationCount;

  public Configuration() {
  }

  public AdvancedConfiguration getAdvancedConfiguration() {
    throw new UnsupportedOperationException("getAdvancedConfiguration should not be called");
  }

  public void loadState(final Element element) {
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
    importPlaces(getDefaultInjections());
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

  private static List<BaseInjection> loadDefaultInjections() {
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
    final THashSet<Object> visited = new THashSet<Object>();
    for (IdeaPluginDescriptor pluginDescriptor : PluginManager.getPlugins()) {
      if (pluginDescriptor instanceof IdeaPluginDescriptorImpl && !((IdeaPluginDescriptorImpl)pluginDescriptor).isEnabled()) continue;
      final ClassLoader loader = pluginDescriptor.getPluginClassLoader();
      if (!visited.add(loader)) continue;
      if (loader instanceof PluginClassLoader && ((PluginClassLoader)loader).getUrls().isEmpty()) continue;
      try {
        final Enumeration<URL> enumeration = loader.getResources("META-INF/languageInjections.xml");
        if (enumeration == null) continue;
        while (enumeration.hasMoreElements()) {
          URL url = enumeration.nextElement();
          if (!visited.add(url.getFile())) continue; // for DEBUG mode
          try {
            cfgList.add(load(url.openStream()));
          }
          catch (Exception e) {
            LOG.warn(e);
          }
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }

    final ArrayList<BaseInjection> defaultInjections = new ArrayList<BaseInjection>();
    for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
      for (Configuration cfg : cfgList) {
        final List<BaseInjection> imported = cfg.getInjections(supportId);
        defaultInjections.addAll(imported);
      }
    }
    return defaultInjections;
  }

  public Element getState() {
    return getState(new Element(COMPONENT_NAME));
  }

  protected Element getState(final Element element) {
    final List<String> injectorIds = new ArrayList<String>(myInjections.keySet());
    Collections.sort(injectorIds);
    for (String key : injectorIds) {
      final List<BaseInjection> injections = new ArrayList<BaseInjection>(myInjections.get(key));
      injections.removeAll(getDefaultInjections());
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

  public static Configuration getProjectInstance(Project project) {
    return ServiceManager.getService(project, Configuration.class);
  }

  public List<BaseInjection> getDefaultInjections() {
    return Collections.emptyList();
  }

  @Nullable
  public static Configuration load(final InputStream is) throws IOException, JDOMException {
    try {
      final Document document = JDOMUtil.loadDocument(is);
      final ArrayList<Element> elements = new ArrayList<Element>();
      final Element rootElement = document.getRootElement();
      final Element state;
      if (rootElement.getName().equals(COMPONENT_NAME)) {
        state = rootElement;
      }
      else {
        elements.add(rootElement);
        elements.addAll(rootElement.getChildren("component"));
        state = ContainerUtil.find(elements, new Condition<Element>() {
          public boolean value(final Element element) {
            return "component".equals(element.getName()) && COMPONENT_NAME.equals(element.getAttributeValue("name"));
          }
        });
      }
      if (state != null) {
        final Configuration cfg = new Configuration();
        cfg.loadState(state);
        return cfg;
      }
      return null;
    }
    finally {
      is.close();
    }
  }

  private int importPlaces(final List<BaseInjection> injections) {
    final Map<String, Set<BaseInjection>> map = ContainerUtil.classify(injections.iterator(), new Convertor<BaseInjection, String>() {
      @Override
      public String convert(final BaseInjection o) {
        return o.getSupportId();
      }
    });
    final ArrayList<BaseInjection> originalInjections = new ArrayList<BaseInjection>();
    final ArrayList<BaseInjection> newInjections = new ArrayList<BaseInjection>();
    for (String supportId : InjectorUtils.getActiveInjectionSupportIds()) {
      final Set<BaseInjection> importingInjections = map.get(supportId);
      if (importingInjections == null) continue;
      importInjections(getInjections(supportId), importingInjections, originalInjections, newInjections);
    }
    if (!newInjections.isEmpty()) configurationModified();
    replaceInjections(newInjections, originalInjections);
    return newInjections.size();
  }

  static void importInjections(final Collection<BaseInjection> existingInjections, final Collection<BaseInjection> importingInjections,
                               final Collection<BaseInjection> originalInjections, final Collection<BaseInjection> newInjections) {
    final MultiValuesMap<InjectionPlace, BaseInjection> placeMap = new MultiValuesMap<InjectionPlace, BaseInjection>();
    for (BaseInjection exising : existingInjections) {
      for (InjectionPlace place : exising.getInjectionPlaces()) {
        placeMap.put(place, exising);
      }
    }
    main: for (BaseInjection other : importingInjections) {
      final List<BaseInjection> matchingInjections = ContainerUtil.concat(other.getInjectionPlaces(), new Function<InjectionPlace, Collection<? extends BaseInjection>>() {
        public Collection<? extends BaseInjection> fun(final InjectionPlace o) {
          final Collection<BaseInjection> collection = placeMap.get(o);
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

  public void configurationModified() {
    myModificationCount ++;
  }

  public long getModificationCount() {
    return myModificationCount;
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
            newPlaces.add(place.enabled(enabled));
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

  protected void setInjections(Collection<BaseInjection> injections) {
    for (BaseInjection injection : injections) {
      myInjections.get(injection.getSupportId()).add(injection);
    }
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
                                public boolean process(final List<? extends BaseInjection> add,
                                                       final List<? extends BaseInjection> remove) {
                                  replaceInjectionsWithUndoInner(add, remove);
                                  FileContentUtil.reparseOpenedFiles();
                                  return true;
                                }
                              });
  }

  protected void replaceInjectionsWithUndoInner(final List<? extends BaseInjection> add, final List<? extends BaseInjection> remove) {
    replaceInjections(add, remove);
  }

  public static <T> void replaceInjectionsWithUndo(final Project project, final T add, final T remove,
                                final List<? extends PsiElement> psiElementsToRemove,
                                final PairProcessor<T, T> actualProcessor) {
    final UndoableAction action = new GlobalUndoableAction() {
      public void undo() {
        actualProcessor.process(remove, add);
      }

      public void redo() {
        actualProcessor.process(add, remove);
      }
    };
    final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(psiElementsToRemove, new NullableFunction<PsiElement, PsiFile>() {
      public PsiFile fun(final PsiElement psiAnnotation) {
        return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
      }
    });
    new WriteCommandAction.Simple(project, "Language Injection Configuration Update", PsiUtilBase.toPsiFileArray(psiFiles)) {
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
      myInjections.get(injection.getSupportId()).add(injection);
    }
    configurationModified();
  }

  public static class AdvancedConfiguration {
    // runtime pattern validation instrumentation
    @NotNull private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

    // annotation class names
    @NotNull private String myLanguageAnnotation;
    @NotNull private String myPatternAnnotation;
    @NotNull private String mySubstAnnotation;

    private boolean myIncludeUncomputablesAsLiterals;
    private DfaOption myDfaOption = DfaOption.RESOLVE;

    // cached annotation name pairs
    private Pair<String, ? extends Set<String>> myLanguageAnnotationPair;
    private Pair<String, ? extends Set<String>> myPatternAnnotationPair;

    private Pair<String, ? extends Set<String>> mySubstAnnotationPair;

    public AdvancedConfiguration() {
      setLanguageAnnotation("org.intellij.lang.annotations.Language");
      setPatternAnnotation("org.intellij.lang.annotations.Pattern");
      setSubstAnnotation("org.intellij.lang.annotations.Subst");
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

    public boolean isIncludeUncomputablesAsLiterals() {
      return myIncludeUncomputablesAsLiterals;
    }

    public void setIncludeUncomputablesAsLiterals(boolean flag) {
      myIncludeUncomputablesAsLiterals = flag;
    }

    @NotNull
    public DfaOption getDfaOption() {
      return myDfaOption;
    }

    public void setDfaOption(@NotNull final DfaOption dfaOption) {
      myDfaOption = dfaOption;
    }


    public InstrumentationType getInstrumentation() {
      return myInstrumentationType;
    }

    private void writeState(final Element element) {
      JDOMExternalizerUtil.writeField(element, INSTRUMENTATION_TYPE_NAME, myInstrumentationType.toString());
      JDOMExternalizerUtil.writeField(element, LANGUAGE_ANNOTATION_NAME, myLanguageAnnotation);
      JDOMExternalizerUtil.writeField(element, PATTERN_ANNOTATION_NAME, myPatternAnnotation);
      JDOMExternalizerUtil.writeField(element, SUBST_ANNOTATION_NAME, mySubstAnnotation);
      switch (myDfaOption) {
        case OFF:
          break;
        case RESOLVE:
          JDOMExternalizerUtil.writeField(element, RESOLVE_REFERENCES, Boolean.TRUE.toString());
          break;
        case ASSIGNMENTS:
          JDOMExternalizerUtil.writeField(element, LOOK_FOR_VAR_ASSIGNMENTS, Boolean.TRUE.toString());
          break;
        case DFA:
          JDOMExternalizerUtil.writeField(element, USE_DFA_IF_AVAILABLE, Boolean.TRUE.toString());
          break;
      }
    }

    private void loadState(final Element element) {
      setInstrumentationType(JDOMExternalizerUtil.readField(element, INSTRUMENTATION_TYPE_NAME));
      setLanguageAnnotation(JDOMExternalizerUtil.readField(element, LANGUAGE_ANNOTATION_NAME));
      setPatternAnnotation(JDOMExternalizerUtil.readField(element, PATTERN_ANNOTATION_NAME));
      setSubstAnnotation(JDOMExternalizerUtil.readField(element, SUBST_ANNOTATION_NAME));
      if (readBoolean(element, RESOLVE_REFERENCES, true)) {
        setDfaOption(DfaOption.RESOLVE);
      }
      if (readBoolean(element, LOOK_FOR_VAR_ASSIGNMENTS, false)) {
        setDfaOption(DfaOption.ASSIGNMENTS);
      }
      if (readBoolean(element, USE_DFA_IF_AVAILABLE, false)) {
        setDfaOption(DfaOption.DFA);
      }
      setIncludeUncomputablesAsLiterals(readBoolean(element, INCLUDE_UNCOMPUTABLES_AS_LITERALS, false));
    }
  }
}
