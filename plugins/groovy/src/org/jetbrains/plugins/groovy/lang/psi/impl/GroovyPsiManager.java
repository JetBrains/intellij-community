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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultGroovyMethod;

import java.util.*;

/**
 * @author ven
 */
public class GroovyPsiManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private Project myProject;

  private Map<String, List<PsiMethod>> myDefaultMethods;

  private static final String DEFAULT_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  private static final String DEFAULT_STATIC_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods";
  private static final String SWING_BUILDER_QNAME = "groovy.swing.SwingBuilder";

  private GrTypeDefinition myArrayClass;

  private final ConcurrentWeakHashMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();
  private boolean myRebuildGdkPending = true;

  public TypeInferenceHelper getTypeInferenceHelper() {
    return myTypeInferenceHelper;
  }

  private TypeInferenceHelper myTypeInferenceHelper;

  private static final String SYNTHETIC_CLASS_TEXT = "class __ARRAY__ { public int length }";

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
    ((PsiManagerEx) PsiManager.getInstance(myProject)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        myCalculatedTypes.clear();
      }
    });

    myTypeInferenceHelper = new  TypeInferenceHelper(myProject);

    ProjectRootManager.getInstance(myProject).addModuleRootListener(new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {}

      public void rootsChanged(ModuleRootEvent event) {
        myRebuildGdkPending = true;
      }
    });
  }

  public void buildGDK() {
    if (myProject.isDisposed()) return;

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setIndeterminate(true);
      indicator.setText(GroovyBundle.message("reading.gdk.classes"));
    }

    buildGDKImpl();

    if (indicator != null) indicator.popState();
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

    addSwingBuilderMethods();
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
      "actions", "java.util.List",
      "map", "java.util.Map",
      "buttonGroup", "javax.swing.ButtonGroup",
      "bind", "org.codehaus.groovy.binding.BindingUpdatable",
      "model", "org.codehaus.groovy.binding.ModelBinding",
      "widget", "java.awt.Component",
      "container", "java.awt.Container",
      "bean", "java.lang.Object",
      "dialog", "javax.swing.JDialog",
      "fileChooser", "javax.swing.JFileChooser",
      "frame", "javax.swing.JFrame",
      "optionPane", "javax.swing.JOptionPane",
      "window", "javax.swing.JWindow",
      "button", "javax.swing.JButton",
      "checkBox", "javax.swing.JCheckBox",
      "checkBoxMenuItem", "javax.swing.JCheckBoxMenuItem",
      "menuItem", "javax.swing.JMenuItem",
      "radioButton", "javax.swing.JRadioButton",
      "radioButtonMenuItem", "javax.swing.JRadioButtonMenuItem",
      "toggleButton", "javax.swing.JToggleButton",

      "editorPane", "javax.swing.JEditorPane",
      "label", "javax.swing.JLabel",
      "passwordField", "javax.swing.JPasswordField",
      "textArea", "javax.swing.JTextArea",
      "textField", "javax.swing.JTextField",
      "textPane", "javax.swing.JTextPane",

      "colorChooser", "javax.swing.JColorChooser",
      "comboBox", "javax.swing.JComboBox",
      "desktopPane", "javax.swing.JDesktopPane",
      "formattedTextField", "javax.swing.JFormattedTextField",
      "internalFrame", "javax.swing.JInternalFrame",
      "layeredPane", "javax.swing.JLayeredPane",
      "list", "javax.swing.JList",
      "menu", "javax.swing.JMenu",
      "menuBar", "javax.swing.JMenuBar",
      "panel", "javax.swing.JPanel",
      "popupMenum", "javax.swing.JPopupMenu",
      "progressBar", "javax.swing.JProgressBar",
      "scrollBar", "javax.swing.JScrollBar",
      "scrollPane", "javax.swing.JScrollPane",
      "separator", "javax.swing.JSeparator",
      "slider", "javax.swing.JSlider",
      "spinner", "javax.swing.JSpinner",
      "splitPane", "javax.swing.JSplitPane",
      "tabbedPane", "javax.swing.JTabbedPane",
      "table", "javax.swing.JTable",
      "tableColumn", "javax.swing.table.TableColumn",
      "toolbar", "javax.swing.JToolbar",
      "tree", "javax.swing.JTree",
      "viewport", "javax.swing.JViewport",
      "boundedRangeModel", "javax.swing.DefaultBoundedRangeModel",
      "spinnerDateModel", "javax.swing.SpinnerDateModel",
      "spinnerListModel", "javax.swing.SpinnerListModel",
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
      "box", "javax.swing.Box",
      "hbox", "javax.swing.Box",
      "hglue", "java.awt.Component",
      "hstrut", "java.awt.Component",
      "vbox", "javax.swing.Box",
      "vglue", "java.awt.Component",
      "vstrut", "java.awt.Component",
      "glue", "java.awt.Component",
      "rigidArea", "java.awt.Component",
      "tableLayout", "groovy.swing.impl.TableLayoutRow",
      "tr", "groovy.swing.impl.TableLayoutRow",
      "td", "groovy.swing.impl.TableLayoutCell",
  };

  private void addSwingBuilderMethods() {
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
  }


  public List<PsiMethod> getDefaultMethods(String qName) {
    if (myRebuildGdkPending) {
      synchronized (this) {
        if (myRebuildGdkPending) {
          buildGDKImpl();
          myRebuildGdkPending = false;
        }
      }
    }

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
        myArrayClass = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition(SYNTHETIC_CLASS_TEXT);
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
