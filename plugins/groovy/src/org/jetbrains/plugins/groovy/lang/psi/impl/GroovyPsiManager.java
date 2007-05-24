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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class GroovyPsiManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private Project myProject;

  private Map<String, List<PsiMethod>> myDefaultMethods;
  private MessageBusConnection myRootConnection;
  private static final String DEFAULT_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  private static final String SWING_BUILDER_QNAME = "groovy.swing.SwingBuilder";

  public GroovyPsiManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Psi Manager";
  }

  public void initComponent() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        fillDefaultGroovyMethods();
      }
    });

    myRootConnection = myProject.getMessageBus().connect();
    ModuleRootListener moduleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        if (!ApplicationManager.getApplication().isUnitTestMode() && myProject.isInitialized()) {
          fillDefaultGroovyMethods();
        }
      }
    };

    myRootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, moduleRootListener);
  }

  private void fillDefaultGroovyMethods() {
    myDefaultMethods = new HashMap<String, List<PsiMethod>>();

    PsiClass defaultMethodsClass = PsiManager.getInstance(myProject).findClass(DEFAULT_METHODS_QNAME, GlobalSearchScope.allScope(myProject));
    if (defaultMethodsClass != null) {
      for (PsiMethod method : defaultMethodsClass.getMethods()) {
        if (method.isConstructor()) continue;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        LOG.assertTrue(parameters.length > 0);
        PsiType thisType = parameters[0].getType();
        if (!(thisType instanceof PsiClassType)) continue;
        PsiClass resolved = ((PsiClassType) thisType).resolve();
        if (resolved == null) continue;
        String thisQName = resolved.getQualifiedName();
        LOG.assertTrue(thisQName != null);
        List<PsiMethod> hisMethods = myDefaultMethods.get(thisQName);
        if (hisMethods == null) {
          hisMethods = new ArrayList<PsiMethod>();
          myDefaultMethods.put(thisQName, hisMethods);
        }
        hisMethods.add(convertToNonStatic(method));
      }

      try {
        addSwingBuilderMethods();
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
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
      "tabbededPane", "javax.swing.JTabbedPane",
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
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (int i = 0; i < SWING_WIDGETS_METHODS.length / 2; i++) {
      String methodName = SWING_WIDGETS_METHODS[2 * i];
      String returnTypeText = SWING_WIDGETS_METHODS[2 * i + 1];
      PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      PsiClassType returnType = factory.createTypeByFQClassName(returnTypeText, scope);
      methods.add(factory.createMethod(methodName, returnType));
    }

    myDefaultMethods.put(SWING_BUILDER_QNAME, methods);
  }

  private PsiMethod convertToNonStatic(PsiMethod method) {
    return new DefaultGroovyMethod(method, null);
  }

  public void disposeComponent() {
    myRootConnection.disconnect();
  }


  public List<PsiMethod> getDefaultMethods(String qName) {
    if (myDefaultMethods == null) {
      fillDefaultGroovyMethods();
    }

    List<PsiMethod> methods = myDefaultMethods.get(qName);
    if (methods == null) return Collections.emptyList();
    return methods;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return project.getComponent(GroovyPsiManager.class);
  }
}
