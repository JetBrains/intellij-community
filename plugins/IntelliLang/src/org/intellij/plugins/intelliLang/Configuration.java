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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
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
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
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
public final class Configuration implements PersistentStateComponent<Element> {
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

  private final Map<String, List<BaseInjection>> myInjections = new ConcurrentFactoryMap<String, List<BaseInjection>>() {
    @Override
    protected List<BaseInjection> create(final String key) {
      return new CopyOnWriteArrayList<BaseInjection>();
    }
  };

  // runtime pattern validation instrumentation
  @NotNull private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

  // annotation class names
  @NotNull private String myLanguageAnnotation;
  @NotNull private String myPatternAnnotation;
  @NotNull private String mySubstAnnotation;

  private boolean myResolveReferences;

  // cached annotation name pairs
  private Pair<String, ? extends Set<String>> myLanguageAnnotationPair;
  private Pair<String, ? extends Set<String>> myPatternAnnotationPair;
  private Pair<String, ? extends Set<String>> mySubstAnnotationPair;

  private volatile long myModificationCount;
  private boolean myMergeWithDefault;

  public Configuration() {
    myMergeWithDefault = true;
    setLanguageAnnotation("org.intellij.lang.annotations.Language");
    setPatternAnnotation("org.intellij.lang.annotations.Pattern");
    setSubstAnnotation("org.intellij.lang.annotations.Subst");
  }

  public void loadState(final Element element) {
    final THashMap<String, LanguageInjectionSupport> supports = new THashMap<String, LanguageInjectionSupport>();
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      supports.put(support.getId(), support);
    }
    myInjections.get(LanguageInjectionSupport.XML_SUPPORT_ID).addAll(readExternal(element.getChild(TAG_INJECTION_NAME), new Factory<XmlTagInjection>() {
      public XmlTagInjection create() {
        return new XmlTagInjection();
      }
    }));
    myInjections.get(LanguageInjectionSupport.XML_SUPPORT_ID).addAll(readExternal(element.getChild(ATTRIBUTE_INJECTION_NAME), new Factory<XmlAttributeInjection>() {
      public XmlAttributeInjection create() {
        return new XmlAttributeInjection();
      }
    }));
    myInjections.get(LanguageInjectionSupport.JAVA_SUPPORT_ID).addAll(readExternal(element.getChild(PARAMETER_INJECTION_NAME), new Factory<MethodParameterInjection>() {
      public MethodParameterInjection create() {
        return new MethodParameterInjection();
      }
    }));
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
    final String resolveReferences = JDOMExternalizerUtil.readField(element, RESOLVE_REFERENCES);
    setResolveReferences(resolveReferences == null || Boolean.parseBoolean(resolveReferences));
    if (myMergeWithDefault) {
      mergeWithDefaultConfiguration();
      // todo init places here in order not to run twice??
    }
  }

  private void mergeWithDefaultConfiguration() {
    try {
      final Configuration cfg = load(getClass().getClassLoader().getResourceAsStream("/" + COMPONENT_NAME + ".xml"));
      if (cfg == null) return; // very strange
      importFrom(cfg);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  public Element getState() {
    final Element element = new Element(COMPONENT_NAME);

    JDOMExternalizerUtil.writeField(element, INSTRUMENTATION_TYPE_NAME, myInstrumentationType.toString());
    JDOMExternalizerUtil.writeField(element, LANGUAGE_ANNOTATION_NAME, myLanguageAnnotation);
    JDOMExternalizerUtil.writeField(element, PATTERN_ANNOTATION_NAME, myPatternAnnotation);
    JDOMExternalizerUtil.writeField(element, SUBST_ANNOTATION_NAME, mySubstAnnotation);
    JDOMExternalizerUtil.writeField(element, RESOLVE_REFERENCES, String.valueOf(myResolveReferences));

    final List<String> injectoIds = new ArrayList<String>(myInjections.keySet());
    Collections.sort(injectoIds);
    for (String key : injectoIds) {
      final List<BaseInjection> injections = new ArrayList<BaseInjection>(myInjections.get(key));
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
  public static Configuration load(final InputStream is) throws IOException, JDOMException, InvalidDataException {
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
        cfg.setMergeWithDefault(false);
        cfg.loadState(element);
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
   */
  public int importFrom(final Configuration cfg) {
    int n = 0;
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      final List<BaseInjection> mineInjections = myInjections.get(support.getId());
      for (BaseInjection other : cfg.getInjections(support.getId())) {
        final BaseInjection existing = findExistingInjection(other);
        if (existing == null) {
          n ++;
          mineInjections.add(other);
        }
        else {
          if (existing.equals(other)) continue;
          boolean placesAdded = false;
          for (InjectionPlace place : other.getInjectionPlaces()) {
            if (existing.findPlaceByText(place.getText()) == null) {
              existing.getInjectionPlaces().add(place);
              placesAdded = true;
            }
          }
          if (placesAdded) n++;
        }
      }
    }
    if (n >= 0) configurationModified();
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
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      for (BaseInjection injection : getInjections(support.getId())) {
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

  public void setMergeWithDefault(final boolean mergeWithDefault) {
    myMergeWithDefault = mergeWithDefault;
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
    return Collections.unmodifiableSet(new THashSet<String>(myInjections.keySet()));
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
      public void undo() throws UnexpectedUndoException {
        actualProcessor.process(remove, add);
      }

      public void redo() throws UnexpectedUndoException {
        actualProcessor.process(add, remove);
      }

      public DocumentReference[] getAffectedDocuments() {
        return DocumentReference.EMPTY_ARRAY;
      }

      public boolean isComplex() {
        return true;
      }

    };
    final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(psiElementsToRemove, new NullableFunction<PsiElement, PsiFile>() {
      public PsiFile fun(final PsiElement psiAnnotation) {
        return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
      }
    });
    new WriteCommandAction.Simple(project, psiFiles.toArray(new PsiFile[psiFiles.size()])) {
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
      injection.initializePlaces();
      myInjections.get(injection.getSupportId()).add(injection);
    }
    configurationModified();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }


}
