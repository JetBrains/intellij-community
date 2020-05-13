package org.jetbrains.plugins.javaFX.sceneBuilder;// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.selection.AbstractSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.editor.selection.ObjectSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.fxom.*;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.LibraryItem;
import com.oracle.javafx.scenebuilder.kit.metadata.util.PropertyName;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Predicate;

/// Warning!
/// It is loaded by SceneBuilderUtil to be compatible with 8 and 11 java
public class SceneBuilderImpl implements SceneBuilder {
  private static final Logger LOG = Logger.getInstance(SceneBuilderImpl.class);
  private static final PropertyName ourChildrenPropertyName = new PropertyName("children");

  private final URL myFileURL;
  private final Project myProject;
  private final EditorCallback myEditorCallback;
  private final JComponent myPanel = JavaFXPlatformHelper.createJFXPanel();

  protected EditorController myEditorController;
  private URLClassLoader myClassLoader;
  private volatile Collection<CustomComponent> myCustomComponents;
  private volatile boolean mySkipChanges;
  private Object myListener;
  private Object mySelectionListener;
  private List<List<SelectionNode>> mySelectionState;
  @NotNull protected final ClassLoader myParentClassLoader;

  public SceneBuilderImpl(URL url, Project project, EditorCallback editorCallback, @NotNull ClassLoader loader) {
    myFileURL = url;
    myProject = project;
    myEditorCallback = editorCallback;
    myParentClassLoader = loader;

    JavaFXPlatformHelper.disableImplicitExit();

    final DumbService dumbService = DumbService.getInstance(myProject);
    if (dumbService.isDumb()) {
      dumbService.smartInvokeLater(() -> JavaFXPlatformHelper.javafxInvokeLater(this::create));
    }
    else {
      JavaFXPlatformHelper.javafxInvokeLater(this::create);
    }
  }

  private void create() {
    if (myProject.isDisposed()) {
      return;
    }
    Thread.currentThread().setUncaughtExceptionHandler(SceneBuilderImpl::logUncaughtException);

    myEditorController = new EditorController();
    updateCustomLibrary();
    JavaFXPlatformHelper.setupJFXPanel(myPanel, myEditorController);

    loadFile();

    myListener = JavaFXPlatformHelper.createChangeListener(() -> {
      if (!mySkipChanges) {
        myEditorCallback.saveChanges(myEditorController.getFxmlText());
      }
    });
    mySelectionListener = JavaFXPlatformHelper.createChangeListener(() -> {
      if (!mySkipChanges) {
        mySelectionState = getSelectionState();
      }
    });

    JavaFXPlatformHelper.addListeners(myEditorController, myListener, mySelectionListener);
  }

  private void updateCustomLibrary() {
    final URLClassLoader oldClassLoader = myClassLoader;
    myClassLoader = createProjectContentClassLoader(myProject, myParentClassLoader);
    JavaFXPlatformHelper.setDefaultClassLoader(myClassLoader);

    if (oldClassLoader != null) {
      try {
        oldClassLoader.close();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    final Collection<CustomComponent> customComponents = DumbService.getInstance(myProject)
      .runReadActionInSmartMode(this::collectCustomComponents);

    try {
      final CustomLibrary customLibrary = new CustomLibrary(myClassLoader, customComponents);
      myEditorController.setLibrary(customLibrary);
      myCustomComponents = customComponents;
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private Collection<CustomComponent> collectCustomComponents() {
    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    final PsiClass nodeClass = JavaPsiFacade.getInstance(myProject)
      .findClass("javafx.scene.Node", GlobalSearchScope.allScope(myProject));
    if (nodeClass == null) {
      return Collections.emptyList();
    }

    final Collection<PsiClass> psiClasses = CachedValuesManager.getCachedValue(nodeClass, () -> {
      // Take custom components from libraries, but not from the project modules, because org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilder instantiates the components' classes.
      // Modules might be not compiled or may change since last compile, it's too expensive to keep track of that.
      final GlobalSearchScope scope = ProjectScope.getLibrariesScope(nodeClass.getProject());
      final JavaSdkVersion ideJdkVersion = JavaSdkVersion.fromJavaVersion(JavaVersion.current());
      final LanguageLevel ideLanguageLevel = ideJdkVersion != null ? ideJdkVersion.getMaxLanguageLevel() : null;
      final Query<PsiClass> query = ClassInheritorsSearch.search(nodeClass, scope, true, true, false);
      final Set<PsiClass> result = new THashSet<>();
      query.forEach(psiClass -> {
        if (psiClass.hasModifierProperty(PsiModifier.PUBLIC) &&
            !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !isBuiltInComponent(psiClass) &&
            isCompatibleLanguageLevel(psiClass, ideLanguageLevel)) {
          result.add(psiClass);
        }
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
    if (psiClasses.isEmpty()) {
      return Collections.emptyList();
    }

    return prepareCustomComponents(psiClasses);
  }

  @NotNull
  private Collection<CustomComponent> prepareCustomComponents(Collection<PsiClass> psiClasses) {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    final Map<String, BuiltinComponent> builtinComponents =
      loadBuiltinComponents(className -> psiFacade.findClass(className, scope) != null);

    final List<CustomComponent> customComponents = new ArrayList<>();
    for (PsiClass psiClass : psiClasses) {
      final String qualifiedName = psiClass.getQualifiedName();
      final String name = psiClass.getName();
      if (qualifiedName != null && name != null) {
        BuiltinComponent parentComponent = null;
        for (PsiClass aClass = psiClass; aClass != null; aClass = aClass.getSuperClass()) {
          final BuiltinComponent component = builtinComponents.get(aClass.getQualifiedName());
          if (component != null) {
            parentComponent = component;
            break;
          }
        }
        final String moduleName = getComponentModuleName(psiClass);
        final Map<String, String> attributes = parentComponent != null ? parentComponent.getAttributes() : Collections.emptyMap();
        customComponents.add(new CustomComponent(name, qualifiedName, moduleName, attributes));
      }
    }
    return customComponents;
  }

  @Nullable
  private String getComponentModuleName(@NotNull PsiClass psiClass) {
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(psiClass);
    if (vFile == null) return null;
    final Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(vFile);
    if (module != null) {
      return module.getName();
    }
    else {
      final OrderEntry entry = LibraryUtil.findLibraryEntry(vFile, myProject);
      if (entry instanceof LibraryOrderEntry) {
        final String libraryName = ((LibraryOrderEntry)entry).getLibraryName();
        if (libraryName != null) {
          return libraryName;
        }
      }
    }
    return null;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  public boolean reload() {
    if (myCustomComponents == null) return false;

    final Collection<CustomComponent> customComponents = DumbService.getInstance(myProject)
      .runReadActionInSmartMode(this::collectCustomComponents);
    if (!new THashSet<>(myCustomComponents).equals(new THashSet<>(customComponents))) return false;

    JavaFXPlatformHelper.javafxInvokeLater(() -> {
      if (myEditorController != null) {
        loadFile();
      }
    });
    return true;
  }

  @Override
  public void close() {
    JavaFXPlatformHelper.javafxInvokeLater(this::closeImpl);
  }

  private void closeImpl() {
    JavaFXPlatformHelper.removeListeners(myEditorController, myListener, mySelectionListener);
    myEditorController = null;
    try {
      if (myClassLoader != null) {
        JavaFXPlatformHelper.setDefaultClassLoader(myParentClassLoader);
        myClassLoader.close();
        myClassLoader = null;
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private void loadFile() {
    mySkipChanges = true;

    try {
      String fxmlText = FXOMDocument.readContentFromURL(myFileURL);
      String editorFxmlText = myEditorController.getFxmlText();
      if (Objects.equals(fxmlText, editorFxmlText)) return;

      myEditorController.setFxmlTextAndLocation(fxmlText, myFileURL);
      restoreSelection(mySelectionState);
    }
    catch (Throwable e) {
      myEditorCallback.handleError(e);
    }
    finally {
      mySkipChanges = false;
    }
  }

  private List<List<SelectionNode>> getSelectionState() {
    AbstractSelectionGroup group = myEditorController.getSelection().getGroup();
    if (group instanceof ObjectSelectionGroup) {
      Set<FXOMObject> items = ((ObjectSelectionGroup)group).getItems();

      List<List<SelectionNode>> state = new ArrayList<>();
      for (FXOMObject item : items) {
        List<SelectionNode> path = new ArrayList<>();

        for (FXOMObject component = item; component != null; component = component.getParentObject()) {
          path.add(new SelectionNode(component));
        }
        Collections.reverse(path);
        state.add(path);
      }

      return state;
    }

    return null;
  }

  private void restoreSelection(List<List<SelectionNode>> state) {
    if (state == null) return;
    Collection<FXOMObject> newSelection = new ArrayList<>();
    FXOMObject rootComponent = myEditorController.getFxomDocument().getFxomRoot();

    for (List<SelectionNode> path : state) {
      FXOMObject component = getSelectedComponent(rootComponent, path, 0);
      if (component != null) newSelection.add(component);
    }
    myEditorController.getSelection().select(newSelection);
  }

  private static void logUncaughtException(Thread t, Throwable e) {
    if (!(e instanceof ControlFlowException)) {
      LOG.info("Uncaught exception in JavaFX " + t, e);
    }
  }

  private static boolean isBuiltInComponent(PsiClass psiClass) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(psiClass);
    if (file == null) return false;
    final List<OrderEntry> entries = ProjectRootManager.getInstance(psiClass.getProject()).getFileIndex().getOrderEntriesForFile(file);
    return entries.stream().anyMatch(entry -> entry instanceof JdkOrderEntry);
  }

  private static boolean isCompatibleLanguageLevel(@NotNull PsiClass aClass, @Nullable LanguageLevel targetLevel) {
    if (targetLevel == null) return true;
    final Project project = aClass.getProject();
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(aClass);
    if (vFile == null) return true;
    Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vFile);
    if (module == null) {
      final OrderEntry entry = LibraryUtil.findLibraryEntry(vFile, project);
      if (entry != null) {
        module = entry.getOwnerModule();
      }
    }
    Sdk jdk = module != null ? ModuleRootManager.getInstance(module).getSdk() : null;
    if (jdk == null) {
      jdk = ProjectRootManager.getInstance(project).getProjectSdk();
    }
    if (jdk == null) return true;
    final JavaSdkVersion jdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    if (jdkVersion != null) {
      return targetLevel.isAtLeast(jdkVersion.getMaxLanguageLevel());
    }
    return true;
  }

  @NotNull
  private static URLClassLoader createProjectContentClassLoader(Project project, @NotNull ClassLoader parentClassLoader) {
    final List<String> pathList = ReadAction.compute(() ->
                                                       OrderEnumerator.orderEntries(project).productionOnly().withoutSdk().recursively()
                                                         .getPathsList().getPathList());

    final List<URL> classpathUrls = new ArrayList<>();
    for (String path : pathList) {
      try {
        URL url = new File(path).toURI().toURL();
        classpathUrls.add(url);
      }
      catch (MalformedURLException e) {
        LOG.info(e);
      }
    }
    return new URLClassLoader(classpathUrls.toArray(new URL[0]), parentClassLoader);
  }

  private static FXOMObject getSelectedComponent(FXOMObject component, List<SelectionNode> path, int step) {
    if (step >= path.size()) return null;
    SelectionNode node = new SelectionNode(component);
    if (node.equals(path.get(step))) {
      if (step == path.size() - 1) return component;

      List<FXOMObject> children = getChildComponents(component);
      if (children.isEmpty()) return null;
      int indexInParent = path.get(step + 1).indexInParent;
      if (indexInParent >= 0 && indexInParent < children.size()) {
        return getSelectedComponent(children.get(indexInParent), path, step + 1);
      }
    }
    return null;
  }

  private static List<FXOMObject> getChildComponents(FXOMObject component) {
    if (component instanceof FXOMInstance) {
      Map<PropertyName, FXOMProperty> properties = ((FXOMInstance)component).getProperties();
      FXOMProperty value = properties.get(ourChildrenPropertyName);
      if (value instanceof FXOMPropertyC) {
        return ((FXOMPropertyC)value).getValues();
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Map<String, BuiltinComponent> loadBuiltinComponents(Predicate<String> psiClassExists) {
    final Map<String, BuiltinComponent> components = new THashMap<>();
    for (LibraryItem item : BuiltinLibrary.getLibrary().getItems()) {
      final Ref<String> refQualifiedName = new Ref<>();
      final List<String> imports = new ArrayList<>();
      final Map<String, String> attributes = new THashMap<>();
      final Ref<Boolean> rootTagProcessed = new Ref<>(false);
      NanoXmlUtil.parse(new StringReader(item.getFxmlText()), new NanoXmlBuilder() {
        @Override
        public void newProcessingInstruction(String target, Reader reader) throws Exception {
          if ("import".equals(target)) {
            final String imported = StreamUtil.readTextFrom(reader);
            imports.add(imported);
          }
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
          if (rootTagProcessed.get()) return;
          if (key != null && value != null && StringUtil.isEmpty(nsPrefix)) {
            attributes.put(key, value);
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) {
          rootTagProcessed.set(true);
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
          if (rootTagProcessed.get()) return;
          for (String imported : imports) {
            if (imported.equals(name) || imported.endsWith("." + name)) {
              refQualifiedName.set(imported);
              break;
            }
            if (imported.endsWith(".*")) {
              String className = imported.substring(0, imported.length() - 1) + name;
              if (psiClassExists.test(className)) {
                refQualifiedName.set(className);
                break;
              }
            }
          }
        }
      });
      final String qualifiedName = refQualifiedName.get();
      if (!StringUtil.isEmpty(qualifiedName)) {
        final BuiltinComponent previous = components.get(qualifiedName);
        if (previous == null || previous.getAttributes().size() < attributes.size()) {
          components.put(qualifiedName, new BuiltinComponent(attributes));
        }
      }
    }
    return components;
  }

  static class SelectionNode {
    final String qualifiedName;
    final int indexInParent;

    SelectionNode(FXOMObject component) {
      Object graphObject = component.getSceneGraphObject();
      qualifiedName = graphObject.getClass().getName();

      FXOMPropertyC parentProperty = component.getParentProperty();
      indexInParent = parentProperty != null ? parentProperty.getValues().indexOf(component) : -1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SelectionNode)) return false;

      SelectionNode node = (SelectionNode)o;
      return indexInParent == node.indexInParent && Objects.equals(qualifiedName, node.qualifiedName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(qualifiedName, indexInParent);
    }

    @Override
    public String toString() {
      return indexInParent + ":" + qualifiedName;
    }
  }

  public static class BuiltinComponent {
    private final Map<String, String> myAttributes;

    BuiltinComponent(Map<String, String> attributes) {
      myAttributes = attributes;
    }

    public Map<String, String> getAttributes() {
      return myAttributes;
    }
  }

  public static class CustomComponent {
    private final String myName;
    private final String myQualifiedName;
    private final String myModule;
    private final Map<String, String> myAttributes;

    public CustomComponent(@NotNull String name,
                    @NotNull String qualifiedName,
                    @Nullable String module,
                    @NotNull Map<String, String> attributes) {
      myName = name;
      myQualifiedName = qualifiedName;
      myModule = module;
      myAttributes = attributes;
    }

    public String getDisplayName() {
      return !StringUtil.isEmpty(myModule) ? myName + " (" + myModule + ")" : myName;
    }

    public String getFxmlText() {
      final StringBuilder builder =
        new StringBuilder(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><?import %s?><%s", myQualifiedName, myName));
      myAttributes.forEach((name, value) -> builder.append(String.format(" %s=\"%s\"", name, value.replace("\"", "&quot;"))));
      builder.append("/>");
      return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CustomComponent)) return false;

      CustomComponent c = (CustomComponent)o;
      return myQualifiedName.equals(c.myQualifiedName) && Objects.equals(myModule, c.myModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myQualifiedName, myModule);
    }

    @Override
    public String toString() {
      return myModule != null ? myQualifiedName + "(" + myModule + ")" : myQualifiedName;
    }
  }

  public static class CustomLibrary extends Library {
    private static final String CUSTOM_SECTION = "Custom";

    public CustomLibrary(ClassLoader classLoader, Collection<CustomComponent> customComponents) {
      classLoaderProperty.set(classLoader);

      getItems().setAll(BuiltinLibrary.getLibrary().getItems());
      final List<LibraryItem> items = ContainerUtil.map(
        customComponents, component -> new LibraryItem(component.getDisplayName(), CUSTOM_SECTION, component.getFxmlText(), null, this));
      getItems().addAll(items);
    }

    @Override
    public Comparator<String> getSectionComparator() {
      return CustomLibrary::compareSections;
    }

    private static int compareSections(String s1, String s2) {
      final boolean isCustom1 = CUSTOM_SECTION.equals(s1);
      final boolean isCustom2 = CUSTOM_SECTION.equals(s2);
      if (isCustom1) return isCustom2 ? 0 : 1;
      if (isCustom2) return -1;
      return BuiltinLibrary.getLibrary().getSectionComparator().compare(s1, s2);
    }
  }
}
