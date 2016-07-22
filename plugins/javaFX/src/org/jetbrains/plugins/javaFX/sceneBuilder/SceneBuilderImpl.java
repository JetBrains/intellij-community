package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.xml.NanoXmlUtil;
import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.selection.AbstractSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.editor.selection.ObjectSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.fxom.*;
import com.oracle.javafx.scenebuilder.kit.library.BuiltinLibrary;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.LibraryItem;
import com.oracle.javafx.scenebuilder.kit.metadata.util.PropertyName;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

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

/**
 * @author Alexander Lobas
 */
public class SceneBuilderImpl implements SceneBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderImpl");

  private final URL myFileURL;
  private final Project myProject;
  private final EditorCallback myEditorCallback;
  private final JFXPanel myPanel = new JFXPanel();
  private EditorController myEditorController;
  private URLClassLoader myClassLoader;
  private volatile boolean mySkipChanges;
  private ChangeListener<Number> myListener;
  private ChangeListener<Number> mySelectionListener;
  private final Map<String, int[][]> mySelectionState = new FixedHashMap<>(16);

  public SceneBuilderImpl(URL url, Project project, EditorCallback editorCallback) {
    myFileURL = url;
    myProject = project;
    myEditorCallback = editorCallback;

    Platform.setImplicitExit(false);

    final DumbService dumbService = DumbService.getInstance(myProject);
    if (dumbService.isDumb()) {
      dumbService.smartInvokeLater(() -> Platform.runLater(this::create));
    }
    else {
      Platform.runLater(this::create);
    }
  }

  private void create() {
    if (myProject.isDisposed()) {
      return;
    }

    final Collection<CustomComponent> customComponents = DumbService.getInstance(myProject)
      .runReadActionInSmartMode(this::collectCustomComponents);
    myClassLoader = createProjectContentClassLoader(myProject);
    FXMLLoader.setDefaultClassLoader(myClassLoader);

    myEditorController = new EditorController();
    if (!customComponents.isEmpty()) {
      myEditorController.setLibrary(new CustomLibrary(myClassLoader, customComponents));
    }
    HierarchyTreeViewController componentTree = new HierarchyTreeViewController(myEditorController);
    ContentPanelController canvas = new ContentPanelController(myEditorController);
    InspectorPanelController propertyTable = new InspectorPanelController(myEditorController);
    LibraryPanelController palette = new LibraryPanelController(myEditorController);

    SplitPane leftPane = new SplitPane();
    leftPane.setOrientation(Orientation.VERTICAL);
    leftPane.getItems().addAll(palette.getPanelRoot(), componentTree.getPanelRoot());
    leftPane.setDividerPositions(0.5, 0.5);

    SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
    SplitPane.setResizableWithParent(propertyTable.getPanelRoot(), Boolean.FALSE);

    SplitPane mainPane = new SplitPane();

    mainPane.getItems().addAll(leftPane, canvas.getPanelRoot(), propertyTable.getPanelRoot());
    mainPane.setDividerPositions(0.11036789297658862, 0.8963210702341137);

    myPanel.setScene(new Scene(mainPane, myPanel.getWidth(), myPanel.getHeight(), true, SceneAntialiasing.BALANCED));

    loadFile();
    startChangeListener();

    if (myProject.isDisposed()) {
      return;
    }
    UsageTrigger.trigger("scene-builder.open");
  }

  private Collection<CustomComponent> collectCustomComponents() {
    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    final PsiClass nodeClass = JavaPsiFacade.getInstance(myProject)
      .findClass(JavaFxCommonNames.JAVAFX_SCENE_NODE, GlobalSearchScope.allScope(myProject));
    if (nodeClass == null) {
      return Collections.emptyList();
    }

    final Collection<PsiClass> psiClasses = CachedValuesManager.getCachedValue(nodeClass, () -> {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(nodeClass.getProject());
      final String ideJdkVersion = Object.class.getPackage().getSpecificationVersion();
      final LanguageLevel ideLanguageLevel = LanguageLevel.parse(ideJdkVersion);
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
      return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
    if (psiClasses.isEmpty()) {
      return Collections.emptyList();
    }

    return prepareCustomComponents(psiClasses);
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
    final String versionString = jdk.getVersionString();
    if (versionString != null) {
      final JavaSdkVersion jdkVersion = JdkVersionUtil.getVersion(versionString);
      if (jdkVersion != null) {
        return targetLevel.isAtLeast(jdkVersion.getMaxLanguageLevel());
      }
    }
    return true;
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

  @NotNull
  private static URLClassLoader createProjectContentClassLoader(Project project) {
    final List<String> pathList = ApplicationManager.getApplication().runReadAction((Computable<List<String>>)() ->
      OrderEnumerator.orderEntries(project).productionOnly().withoutSdk().recursively().getPathsList().getPathList());

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
    return new URLClassLoader(classpathUrls.toArray(new URL[0]), SceneBuilderImpl.class.getClassLoader());
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  private void startChangeListener() {
    myListener = (observable, oldValue, newValue) -> {
      if (!mySkipChanges) {
        myEditorCallback.saveChanges(myEditorController.getFxmlText());
        UsageTrigger.trigger("scene-builder.edit");
      }
    };
    mySelectionListener = (observable, oldValue, newValue) -> {
      if (!mySkipChanges) {
        int[][] state = getSelectionState();
        if (state != null) {
          mySelectionState.put(myEditorController.getFxmlText(), state);
        }
      }
    };

    myEditorController.getJobManager().revisionProperty().addListener(myListener);
    myEditorController.getSelection().revisionProperty().addListener(mySelectionListener);
  }

  @Override
  public void close() {
    Platform.runLater(this::closeImpl);
  }

  private void closeImpl() {
    if (myEditorController != null) {
      if (mySelectionListener != null) {
        myEditorController.getSelection().revisionProperty().removeListener(mySelectionListener);
      }
      if (myListener != null) {
        myEditorController.getJobManager().revisionProperty().removeListener(myListener);
      }
      myEditorController = null;
    }
    try {
      if (myClassLoader != null) {
        FXMLLoader.setDefaultClassLoader(SceneBuilderImpl.class.getClassLoader());
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
      myEditorController.setFxmlTextAndLocation(fxmlText, myFileURL);

      int[][] selectionState = mySelectionState.get(fxmlText);
      if (selectionState != null) {
        restoreSelection(selectionState);
      }
    }
    catch (Throwable e) {
      myEditorCallback.handleError(e);
    }
    finally {
      mySkipChanges = false;
    }
  }

  private int[][] getSelectionState() {
    AbstractSelectionGroup group = myEditorController.getSelection().getGroup();
    if (group instanceof ObjectSelectionGroup) {
      Set<FXOMObject> items = ((ObjectSelectionGroup)group).getItems();
      int[][] state = new int[items.size()][];
      int index = 0;

      for (FXOMObject item : items) {
        IntArrayList path = new IntArrayList();
        componentToPath(item, path);
        state[index++] = path.toArray();
      }

      return state;
    }

    return null;
  }

  private static void componentToPath(FXOMObject component, IntArrayList path) {
    FXOMObject parent = component.getParentObject();

    if (parent != null) {
      path.add(0, component.getParentProperty().getValues().indexOf(component));
      componentToPath(parent, path);
    }
  }

  private void restoreSelection(int[][] state) {
    Collection<FXOMObject> newSelection = new ArrayList<>();
    FXOMObject rootComponent = myEditorController.getFxomDocument().getFxomRoot();

    for (int[] path : state) {
      pathToComponent(newSelection, rootComponent, path, 0);
    }

    myEditorController.getSelection().select(newSelection);
  }

  private static void pathToComponent(Collection<FXOMObject> components, FXOMObject component, int[] path, int index) {
    if (index == path.length) {
      components.add(component);
    }
    else {
      List<FXOMObject> children = Collections.emptyList();
      Map<PropertyName, FXOMProperty> properties = ((FXOMInstance)component).getProperties();
      for (Map.Entry<PropertyName, FXOMProperty> entry : properties.entrySet()) {
        FXOMProperty value = entry.getValue();
        if (value instanceof FXOMPropertyC) {
          children = ((FXOMPropertyC)value).getValues();
          break;
        }
      }

      int componentIndex = path[index];
      if (0 <= componentIndex && componentIndex < children.size()) {
        pathToComponent(components, children.get(componentIndex), path, index + 1);
      }
    }
  }

  @NotNull
  private static Map<String, BuiltinComponent> loadBuiltinComponents(Predicate<String> psiClassExists) {
    final Map<String, BuiltinComponent> components = new THashMap<>();
    for (LibraryItem item : BuiltinLibrary.getLibrary().getItems()) {
      final Ref<String> refQualifiedName = new Ref<>();
      final List<String> imports = new ArrayList<>();
      final Map<String, String> attributes = new THashMap<>();
      final Ref<Boolean> rootTagProcessed = new Ref<>(false);
      NanoXmlUtil.parse(new StringReader(item.getFxmlText()), new NanoXmlUtil.IXMLBuilderAdapter() {
        @Override
        public void newProcessingInstruction(String target, Reader reader) throws Exception {
          if ("import".equals(target)) {
            final String imported = StreamUtil.readTextFrom(reader);
            imports.add(imported);
          }
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
          if (rootTagProcessed.get()) return;
          if (key != null && value != null && StringUtil.isEmpty(nsPrefix)) {
            attributes.put(key, value);
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          rootTagProcessed.set(true);
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
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

  private static class BuiltinComponent {
    private final Map<String, String> myAttributes;

    public BuiltinComponent(Map<String, String> attributes) {
      myAttributes = attributes;
    }

    public Map<String, String> getAttributes() {
      return myAttributes;
    }
  }

  private static class CustomComponent {
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
  }

  private static class CustomLibrary extends Library {
    private static final String CUSTOM_SECTION = "Custom";

    public CustomLibrary(ClassLoader classLoader, Collection<CustomComponent> customComponents) {
      classLoaderProperty.set(classLoader);

      final List<LibraryItem> libraryItems = getItems();
      libraryItems.addAll(BuiltinLibrary.getLibrary().getItems());
      for (CustomComponent component : customComponents) {
        libraryItems.add(new LibraryItem(component.getDisplayName(), CUSTOM_SECTION, component.getFxmlText(), null, this));
      }
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