/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultGroovyMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class GroovyPsiManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private Project myProject;

  private Map<String, List<PsiMethod>> myDefaultMethods;
  private MessageBusConnection myRootConnection;
  private static final String DEFAULT_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  private static final String DEFAULT_STATIC_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods";
  private static final String SWING_BUILDER_QNAME = "groovy.swing.SwingBuilder";

  private GrTypeDefinition myArrayClass;

  private final ConcurrentWeakHashMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();

  public TypeInferenceHelper getTypeInferenceHelper() {
    return myTypeInferenceHelper;
  }

  private TypeInferenceHelper myTypeInferenceHelper;

  private static final String SYNTHETIC_CLASS_TEXT = "class __ARRAY__ { int length }";
  public Runnable myUpdateRunnable;

  private String myGroovyJarUrl = null;
  private long myGroovyJarTimeStamp = -1;

  public GroovyPsiManager(Project project) {
    myProject = project;

    DirectClassInheritorsSearch.INSTANCE.registerExecutor(new GroovyDirectInheritorsSearcher());
  }

  public void projectOpened() {
  }

  public void projectClosed() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Psi Manager";
  }

  public void initComponent() {
    myUpdateRunnable = new Runnable() {
      public void run() {
        buildGDK();
      }
    };

    StartupManager.getInstance(myProject).registerStartupActivity(myUpdateRunnable);

    ((PsiManagerEx) PsiManager.getInstance(myProject)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        myCalculatedTypes.clear();
      }
    });

    myTypeInferenceHelper = new  TypeInferenceHelper(myProject);

    myRootConnection = myProject.getMessageBus().connect();
    ModuleRootListener moduleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        final Application application = ApplicationManager.getApplication();
        if (!application.isUnitTestMode()) {
          if (myProject.isInitialized()) {
            application.invokeLater(myUpdateRunnable);
          }
        }
      }
    };

    myRootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, moduleRootListener);
  }

  public void buildGDK() {
    if (myProject.isDisposed()) return;
    if (checkUpToDate()) return;

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setIndeterminate(true);
      indicator.setText(GroovyBundle.message("reading.gdk.classes"));
    }

    buildGDKImpl();

    if (indicator != null) indicator.popState();
  }

  private boolean checkUpToDate() {
    PsiClass defaultMethodsClass = PsiManager.getInstance(myProject).findClass(DEFAULT_METHODS_QNAME, GlobalSearchScope.allScope(myProject));
    if (defaultMethodsClass != null) {
      final VirtualFile vFile = defaultMethodsClass.getContainingFile().getVirtualFile();
      if(vFile == null || vFile.getFileSystem() != JarFileSystem.getInstance()) return false;
      final VirtualFile vRoot = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
      if(vRoot == null) return false;
      final String url = vFile.getUrl();
      final long timeStamp = vFile.getTimeStamp();
      if (url.equals(myGroovyJarUrl) && timeStamp == myGroovyJarTimeStamp) return true;
      myGroovyJarUrl = url;
      myGroovyJarTimeStamp = timeStamp;
    }

    return false;
  }

  private void buildGDKImpl() {
    final HashMap<String, List<PsiMethod>> newMap = new HashMap<String, List<PsiMethod>>();

    PsiClass defaultMethodsClass = PsiManager.getInstance(myProject).findClass(DEFAULT_METHODS_QNAME, GlobalSearchScope.allScope(myProject));
    if (defaultMethodsClass != null) {
      for (PsiMethod method : defaultMethodsClass.getMethods()) {
        if (method.isConstructor()) continue;
        addDefaultMethod(method, newMap, false);
      }

    }

    PsiClass defaultStaticMethodsClass = PsiManager.getInstance(myProject).findClass(DEFAULT_STATIC_METHODS_QNAME, GlobalSearchScope.allScope(myProject));
    if (defaultStaticMethodsClass != null) {
      for (PsiMethod method : defaultStaticMethodsClass.getMethods()) {
        if (method.isConstructor()) continue;
        addDefaultMethod(method, newMap, true);
      }
    }

    myDefaultMethods = newMap;

    try {
      addSwingBuilderMethods();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void addDefaultMethod(PsiMethod method, HashMap<String, List<PsiMethod>> map, boolean isStatic) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(parameters.length > 0);
    PsiType thisType = parameters[0].getType();
    String thisCanonicalText = thisType.getCanonicalText();
    LOG.assertTrue(thisCanonicalText != null);
    List<PsiMethod> hisMethods = map.get(thisCanonicalText);
    if (hisMethods == null) {
      hisMethods = new ArrayList<PsiMethod>();
      map.put(thisCanonicalText, hisMethods);
    }
    hisMethods.add(convertMethod(method, isStatic));
  }

  private static final String[] SWING_WIDGETS_METHODS = {
      "action", "groovy.swing.impl.DefaultAction",
      "buttonGroup", "javax.swing.ButtonGroup",
      "widget", "java.awt.Component",
      "dialog", "javax.swing.JDialog",
      "frame", "javax.swing.JFrame",
      "fileChooser", "javax.swing.JFileChooser",
      "optionPane", "javax.swing.JOptionPane",
      "button", "javax.swing.JButton",
      "checkBox", "javax.swing.JCheckBox",
      "checkBoxMenuItem", "javax.swing.JCheckBoxMenuItem",
      "colorChooser", "javax.swing.JColorChooser",
      "comboBox", "javax.swing.JComboBox",
      "desktopPane", "javax.swing.JDesktopPane",
      "editorPane", "javax.swing.JEditorPane",
      "formattedTextField", "javax.swing.JFormattedTextField",
      "internalFrame", "javax.swing.JInternalFrame",
      "label", "javax.swing.JLabel",
      "layeredPane", "javax.swing.JLayeredPane",
      "list", "javax.swing.JList",
      "menu", "javax.swing.JMenu",
      "menuBar", "javax.swing.JMenuBar",
      "menuItem", "javax.swing.JMenuItem",
      "panel", "javax.swing.JPanel",
      "passwordField", "javax.swing.JPasswordField",
      "popupMenum", "javax.swing.JPopupMenu",
      "progressBar", "javax.swing.JProgressBar",
      "radioButton", "javax.swing.JRadioButton",
      "radioButtonMenuItem", "javax.swing.JRadioButtonMenuItem",
      "scrollBar", "javax.swing.JScrollBar",
      "scrollPane", "javax.swing.JScrollPane",
      "separator", "javax.swing.JSeparator",
      "slider", "javax.swing.JSlider",
      "spinner", "javax.swing.JSpinner",
      "splitPane", "javax.swing.JSplitPane",
      "tabbedPane", "javax.swing.JTabbedPane",
      "table", "javax.swing.JTable",
      "textArea", "javax.swing.JTextArea",
      "textPane", "javax.swing.JTextPane",
      "textField", "javax.swing.JTextField",
      "toggleButton", "javax.swing.JToggleButton",
      "toolbar", "javax.swing.JToolbar",
      "tree", "javax.swing.JTree",
      "viewport", "javax.swing.JViewport",
      "boundedRangeModel", "javax.swing.DefaultBoundedRangeModel",
      "spinnerDateModel", "javax.swing.SpinnerDateModel",
      "spinnerNumberModel", "javax.swing.SpinnerNumberModel",
      "tableModel", "javax.swing.table.TableModel",
      "propertyColumn", "javax.swing.table.TableColumn",
      "closureColumn", "javax.swing.table.TableColumn",
      "borderLayout", "java.awt.BorderLayout",
      "cardLayout", "java.awt.CardLayout",
      "flowLayout", "java.awt.FlowLayout",
      "gridBagLayout", "java.awt.GridBagLayout",
      "gridLayout", "java.awt.GridLayout",
      "overlayLayout", "java.swing.OverlayLayout",
      "springLayout", "java.swing.SpringLayout",
      "gridBagConstraints", "java.awt.GridBagConstraints",
      "gbc", "java.awt.GridBagConstraints",
      "boxLayout", "javax.swing.BoxLayout",
      "hbox", "javax.swing.Box",
      "hglue", "java.awt.Component",
      "hstrut", "java.awt.Component",
      "vbox", "javax.swing.Box",
      "vglue", "java.awt.Component",
      "vstrut", "java.awt.Component",
      "glue", "java.awt.Component",
      "tableLayout", "groovy.swing.impl.TableLayoutRow",
      "td", "groovy.swing.impl.TableLayoutCell",
  };

  private void addSwingBuilderMethods() throws IncorrectOperationException {
    PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();

    StringBuilder classText = new StringBuilder();
    classText.append("class SwingBuilder {\n");
    for (int i = 0; i < SWING_WIDGETS_METHODS.length / 2; i++) {
      String methodName = SWING_WIDGETS_METHODS[2 * i];
      String returnTypeText = SWING_WIDGETS_METHODS[2 * i + 1];
      classText.append("public ").append(returnTypeText).append(' ').append(methodName).append("() {} \n");
    }

    classText.append('}');

    final PsiJavaFile file = (PsiJavaFile) factory.createFileFromText("Dummy.java", classText.toString());
    final PsiClass clazz = file.getClasses()[0];

    final PsiMethod[] methods = clazz.getMethods();
    List<PsiMethod> result = new ArrayList<PsiMethod>(methods.length);
    for (PsiMethod method : methods) {
      method.putUserData(GrMethod.BUILDER_METHOD, true);
      result.add(method);
    }

    myDefaultMethods.put(SWING_BUILDER_QNAME, result);
  }

  private PsiMethod convertMethod(PsiMethod method, boolean isStatic) {
    return new DefaultGroovyMethod(method, isStatic);
  }

  public void disposeComponent() {
    myRootConnection.disconnect();
  }


  public List<PsiMethod> getDefaultMethods(String qName) {
    if (myDefaultMethods == null) {
      return Collections.emptyList();
    }

    List<PsiMethod> methods = myDefaultMethods.get(qName);
    if (methods == null) return Collections.emptyList();
    return methods;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return project.getComponent(GroovyPsiManager.class);
  }

  public <T extends GroovyPsiElement> PsiType getType(T element, Function<T, PsiType> calculator) {
    PsiType type = myCalculatedTypes.get(element);
    if (type == null) {
      type = calculator.fun(element);
      if (type == null) {
        type = PsiType.NULL;
      }
      type = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, element, type);
    }
    if (!type.isValid()) {
      LOG.error("Type is invalid: " + type);
    }
    return type == PsiType.NULL ? null : type;
  }

  public GrTypeDefinition getArrayClass() {
    if (myArrayClass == null) {
      try {
        myArrayClass = GroovyElementFactory.getInstance(myProject).createTypeDefinition(SYNTHETIC_CLASS_TEXT);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return myArrayClass;
  }

  ThreadLocal<List<PsiElement>> myElementsWithTypesBeingInferred = new ThreadLocal<List<PsiElement>>(){
    protected List<PsiElement> initialValue() {
      return new ArrayList<PsiElement>();
    }
  };

  public PsiType inferType(PsiElement element, Computable<PsiType> computable) {
    final List<PsiElement> curr = myElementsWithTypesBeingInferred.get();
    try{
      curr.add(element);
      return computable.compute();
    } finally {
      curr.remove(element);
    }
  }

  public boolean isTypeBeingInferred(PsiElement element) {
    return myElementsWithTypesBeingInferred.get().contains(element);
  }
}
