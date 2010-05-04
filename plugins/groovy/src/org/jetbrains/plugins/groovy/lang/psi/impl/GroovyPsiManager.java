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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.*;

/**
 * @author ven
 */
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private final Project myProject;

  private Map<String, List<PsiMethod>> myDefaultMethods;

  private static final String DEFAULT_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  private static final String DEFAULT_STATIC_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods";
  private static final String SWING_BUILDER_QNAME = "groovy.swing.SwingBuilder";

  private GrTypeDefinition myArrayClass;

  private final ConcurrentWeakHashMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();
  private volatile boolean myRebuildGdkPending = true;
  private final GroovyShortNamesCache myCache;

  private final TypeInferenceHelper myTypeInferenceHelper;

  private static final String SYNTHETIC_CLASS_TEXT = "class __ARRAY__ { public int length }";

  public GroovyPsiManager(Project project) {
    myProject = project;
    myCache = ContainerUtil.findInstance(project.getExtensions(PsiShortNamesCache.EP_NAME), GroovyShortNamesCache.class);

    ((PsiManagerEx)PsiManager.getInstance(myProject)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        dropTypesCache();
      }
    });

    myTypeInferenceHelper = new TypeInferenceHelper(myProject);

    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        dropTypesCache();
        myRebuildGdkPending = true;
      }
    });
  }

  public TypeInferenceHelper getTypeInferenceHelper() {
    return myTypeInferenceHelper;
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
  }

  @NotNull
  private Map<String, List<PsiMethod>> buildGDK() {
    final HashMap<String, List<PsiMethod>> newMap = new HashMap<String, List<PsiMethod>>();
    addCategoryMethods(DEFAULT_METHODS_QNAME, newMap, new NotNullFunction<PsiMethod, PsiMethod>() {
      @NotNull
      public PsiMethod fun(PsiMethod method) {
        return new GrGdkMethodImpl(method, false);
      }
    });
    addCategoryMethods(DEFAULT_STATIC_METHODS_QNAME, newMap, new NotNullFunction<PsiMethod, PsiMethod>() {
      @NotNull
      public PsiMethod fun(PsiMethod method) {
        return new GrGdkMethodImpl(method, true);
      }
    });

    addSwingBuilderMethods(newMap);
    return newMap;
  }

  public void addCategoryMethods(String fromClass, Map<String, List<PsiMethod>> toMap, NotNullFunction<PsiMethod, PsiMethod> converter) {
    PsiClass categoryClass = JavaPsiFacade.getInstance(myProject).findClass(fromClass, GlobalSearchScope.allScope(myProject));
    if (categoryClass != null) {
      for (PsiMethod method : categoryClass.getMethods()) {
        if (method.isConstructor()) continue;
        if (!method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
        addDefaultMethod(method, toMap, converter);
      }
    }
  }

  private static void addDefaultMethod(PsiMethod method, Map<String, List<PsiMethod>> map, NotNullFunction<PsiMethod, PsiMethod> converter) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(parameters.length > 0, method.getName());
    PsiType thisType = TypeConversionUtil.erasure(parameters[0].getType());
    String thisCanonicalText = thisType.getCanonicalText();
    LOG.assertTrue(thisCanonicalText != null);
    List<PsiMethod> hisMethods = map.get(thisCanonicalText);
    if (hisMethods == null) {
      hisMethods = new ArrayList<PsiMethod>();
      map.put(thisCanonicalText, hisMethods);
    }
    hisMethods.add(converter.fun(method));
  }

  private static final String[] SWING_WIDGETS_METHODS =
    {"action", "groovy.swing.impl.DefaultAction", "actions", "java.util.List", "map", "java.util.Map", "buttonGroup",
      "javax.swing.ButtonGroup", "bind", "org.codehaus.groovy.binding.BindingUpdatable", "model",
      "org.codehaus.groovy.binding.ModelBinding", "widget", "java.awt.Component", "container", "java.awt.Container", "bean",
      "java.lang.Object", "dialog", "javax.swing.JDialog", "fileChooser", "javax.swing.JFileChooser", "frame", "javax.swing.JFrame",
      "optionPane", "javax.swing.JOptionPane", "window", "javax.swing.JWindow", "button", "javax.swing.JButton", "checkBox",
      "javax.swing.JCheckBox", "checkBoxMenuItem", "javax.swing.JCheckBoxMenuItem", "menuItem", "javax.swing.JMenuItem", "radioButton",
      "javax.swing.JRadioButton", "radioButtonMenuItem", "javax.swing.JRadioButtonMenuItem", "toggleButton", "javax.swing.JToggleButton",

      "editorPane", "javax.swing.JEditorPane", "label", "javax.swing.JLabel", "passwordField", "javax.swing.JPasswordField", "textArea",
      "javax.swing.JTextArea", "textField", "javax.swing.JTextField", "textPane", "javax.swing.JTextPane",

      "colorChooser", "javax.swing.JColorChooser", "comboBox", "javax.swing.JComboBox", "desktopPane", "javax.swing.JDesktopPane",
      "formattedTextField", "javax.swing.JFormattedTextField", "internalFrame", "javax.swing.JInternalFrame", "layeredPane",
      "javax.swing.JLayeredPane", "list", "javax.swing.JList", "menu", "javax.swing.JMenu", "menuBar", "javax.swing.JMenuBar", "panel",
      "javax.swing.JPanel", "popupMenum", "javax.swing.JPopupMenu", "progressBar", "javax.swing.JProgressBar", "scrollBar",
      "javax.swing.JScrollBar", "scrollPane", "javax.swing.JScrollPane", "separator", "javax.swing.JSeparator", "slider",
      "javax.swing.JSlider", "spinner", "javax.swing.JSpinner", "splitPane", "javax.swing.JSplitPane", "tabbedPane",
      "javax.swing.JTabbedPane", "table", "javax.swing.JTable", "tableColumn", "javax.swing.table.TableColumn", "toolbar",
      "javax.swing.JToolbar", "tree", "javax.swing.JTree", "viewport", "javax.swing.JViewport", "boundedRangeModel",
      "javax.swing.DefaultBoundedRangeModel", "spinnerDateModel", "javax.swing.SpinnerDateModel", "spinnerListModel",
      "javax.swing.SpinnerListModel", "spinnerNumberModel", "javax.swing.SpinnerNumberModel", "tableModel", "javax.swing.table.TableModel",
      "propertyColumn", "javax.swing.table.TableColumn", "closureColumn", "javax.swing.table.TableColumn", "borderLayout",
      "java.awt.BorderLayout", "cardLayout", "java.awt.CardLayout", "flowLayout", "java.awt.FlowLayout", "gridBagLayout",
      "java.awt.GridBagLayout", "gridLayout", "java.awt.GridLayout", "overlayLayout", "java.swing.OverlayLayout", "springLayout",
      "java.swing.SpringLayout", "gridBagConstraints", "java.awt.GridBagConstraints", "gbc", "java.awt.GridBagConstraints", "boxLayout",
      "javax.swing.BoxLayout", "box", "javax.swing.Box", "hbox", "javax.swing.Box", "hglue", "java.awt.Component", "hstrut",
      "java.awt.Component", "vbox", "javax.swing.Box", "vglue", "java.awt.Component", "vstrut", "java.awt.Component", "glue",
      "java.awt.Component", "rigidArea", "java.awt.Component", "tableLayout", "groovy.swing.impl.TableLayoutRow", "tr",
      "groovy.swing.impl.TableLayoutRow", "td", "groovy.swing.impl.TableLayoutCell",};

  private void addSwingBuilderMethods(Map<String, List<PsiMethod>> myDefaultMethods) {
    PsiFileFactory factory = PsiFileFactory.getInstance(myProject);

    StringBuilder classText = new StringBuilder();
    classText.append("class SwingBuilder {\n");
    for (int i = 0; i < SWING_WIDGETS_METHODS.length / 2; i++) {
      String methodName = SWING_WIDGETS_METHODS[2 * i];
      String returnTypeText = SWING_WIDGETS_METHODS[2 * i + 1];
      classText.append("public ").append(returnTypeText).append(' ').append(methodName).append("() {} \n");
    }

    classText.append('}');

    final PsiJavaFile file = (PsiJavaFile)factory.createFileFromText("Dummy.java", classText.toString());
    final PsiClass clazz = file.getClasses()[0];

    final PsiMethod[] methods = clazz.getMethods();
    List<PsiMethod> result = new ArrayList<PsiMethod>(methods.length);
    for (PsiMethod method : methods) {
      method.putUserData(GrMethod.BUILDER_METHOD, true);
      result.add(method);
    }

    myDefaultMethods.put(SWING_BUILDER_QNAME, result);
  }

  public List<PsiMethod> getDefaultMethods(String qName) {
    if (myRebuildGdkPending) {
      final Map<String, List<PsiMethod>> gdk = buildGDK();
      if (myRebuildGdkPending) {
        myDefaultMethods = gdk;
        myRebuildGdkPending = false;
      }
    }

    List<PsiMethod> methods = myDefaultMethods.get(qName);
    if (methods == null) return Collections.emptyList();
    return methods;
  }

  public List<PsiMethod> getDefaultMethods(PsiClass psiClass) {
    List<PsiMethod> list = new ArrayList<PsiMethod>();
    getDefaultMethodsInner(psiClass, new HashSet<PsiClass>(), list);
    return list;
  }

  public void getDefaultMethodsInner(PsiClass psiClass, Set<PsiClass> watched, List<PsiMethod> methods) {
    if (watched.contains(psiClass)) return;
    watched.add(psiClass);
    methods.addAll(getDefaultMethods(psiClass.getQualifiedName()));
    for (PsiClass aClass : psiClass.getSupers()) {
      getDefaultMethodsInner(aClass, watched, methods);
    }
  }

  public static GroovyPsiManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiManager.class);
  }

  @Nullable
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
      LOG.error("Type is invalid: " + type + "; element: " + element + " of class " + element.getClass());
    }
    return PsiType.NULL.equals(type) ? null : type;
  }

  public GrTypeDefinition getArrayClass() {
    if (myArrayClass == null) {
      try {
        myArrayClass = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition(SYNTHETIC_CLASS_TEXT);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return myArrayClass;
  }

  private static final ThreadLocal<List<PsiElement>> myElementsWithTypesBeingInferred = new ThreadLocal<List<PsiElement>>() {
    protected List<PsiElement> initialValue() {
      return new ArrayList<PsiElement>();
    }
  };

  public static PsiType inferType(PsiElement element, Computable<PsiType> computable) {
    final List<PsiElement> curr = myElementsWithTypesBeingInferred.get();
    try {
      curr.add(element);
      return computable.compute();
    }
    finally {
      curr.remove(element);
    }
  }

  public static boolean isTypeBeingInferred(PsiElement element) {
    return myElementsWithTypesBeingInferred.get().contains(element);
  }

  public GroovyShortNamesCache getNamesCache() {
    return myCache;
  }
}
