package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Query;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.containers.IntArrayList;
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
import gnu.trove.THashSet;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderImpl implements SceneBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderImpl");
  private static final String CUSTOM_SECTION = "Custom";

  private final URL myFileURL;
  private final Project myProject;
  private final EditorCallback myEditorCallback;
  private final JFXPanel myPanel = new JFXPanel();
  private EditorController myEditorController;
  private URLClassLoader myClassLoader;
  private volatile boolean mySkipChanges;
  private ChangeListener<Number> myListener;
  private ChangeListener<Number> mySelectionListener;
  private final Map<String, int[][]> mySelectionState = new FixedHashMap<String, int[][]>(16);

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
    FXMLLoader.setDefaultClassLoader(SceneBuilderImpl.class.getClassLoader());

    final Collection<CustomComponent> customComponents = ApplicationManager.getApplication().runReadAction(
      (Computable<Collection<CustomComponent>>)this::collectCustomComponents);

    myEditorController = new EditorController();
    if (!customComponents.isEmpty()) {
      myClassLoader = createCustomComponentClassLoader(myProject);
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
      final GlobalSearchScope scopeWithoutJdk = getScopeWithoutJdk(nodeClass.getProject());
      final Query<PsiClass> query = ClassInheritorsSearch.search(nodeClass, scopeWithoutJdk, true);
      final Set<PsiClass> result = new THashSet<PsiClass>();
      query.forEach(psiClass -> {
        if (psiClass.hasModifierProperty(PsiModifier.PUBLIC) && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          result.add(psiClass);
        }
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });

    final List<CustomComponent> customComponents = new ArrayList<>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (PsiClass psiClass : psiClasses) {
      final String fqn = psiClass.getQualifiedName();
      final String name = psiClass.getName();
      if (fqn != null && name != null) {
        final Module module = projectFileIndex.getModuleForFile(psiClass.getContainingFile().getVirtualFile());
        customComponents.add(new CustomComponent(name, fqn, module != null ? module.getName() : ""));
      }
    }
    return customComponents;
  }

  @NotNull
  private static GlobalSearchScope getScopeWithoutJdk(Project project) {
    final Ref<GlobalSearchScope> scopeWithoutJdk = new Ref<>(GlobalSearchScope.allScope(project));
    final OrderEnumerator sdkEnumerator = OrderEnumerator.orderEntries(project).sdkOnly();
    sdkEnumerator.forEach(entry -> {
      if (entry instanceof JdkOrderEntry) {
        final GlobalSearchScope jdkScope = LibraryScopeCache.getInstance(project).getScopeForSdk((JdkOrderEntry)entry);
        scopeWithoutJdk.set(scopeWithoutJdk.get().intersectWith(GlobalSearchScope.notScope(jdkScope)));
      }
      return true;
    });
    return scopeWithoutJdk.get();
  }

  @NotNull
  private static URLClassLoader createCustomComponentClassLoader(Project project) {
    final List<String> pathList = ApplicationManager.getApplication().runReadAction((Computable<List<String>>)() ->
      OrderEnumerator.orderEntries(project).productionOnly().withoutSdk().recursively().getPathsList().getPathList());

    final List<URL> classpathUrls = new ArrayList<>();
    for (String path : pathList) {
      try {
        URL url = new File(FileUtil.toSystemIndependentName(path)).toURI().toURL();
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
    myListener = new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!mySkipChanges) {
          myEditorCallback.saveChanges(myEditorController.getFxmlText());
          UsageTrigger.trigger("scene-builder.edit");
        }
      }
    };
    mySelectionListener = new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!mySkipChanges) {
          int[][] state = getSelectionState();
          if (state != null) {
            mySelectionState.put(myEditorController.getFxmlText(), state);
          }
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
    Collection<FXOMObject> newSelection = new ArrayList<FXOMObject>();
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

  private static class CustomComponent {
    private final String myName;
    private final String myFqName;
    private final String myModule;

    public CustomComponent(@NotNull String name, @NotNull String fqName, @NotNull String module) {
      myName = name;
      myFqName = fqName;
      myModule = module;
    }

    public String getDisplayName() {
      return !StringUtil.isEmpty(myModule) ? myName + "(" + myModule + ")" : myName;
    }

    public String getFxmlText() {
      return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><?import %s?><%s/>", myFqName, myName);
    }
  }

  private static class CustomLibrary extends Library {
    public CustomLibrary(ClassLoader classLoader, Collection<CustomComponent> customComponents) {
      classLoaderProperty.set(classLoader);

      final List<LibraryItem> libraryItems = getItems();
      libraryItems.addAll(BuiltinLibrary.getLibrary().getItems());
      for (CustomComponent component : customComponents) {
        final LibraryItem item = new LibraryItem(component.getDisplayName(), CUSTOM_SECTION, component.getFxmlText(), null, this);
        libraryItems.add(item);
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