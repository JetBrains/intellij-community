/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.swingBuilder;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.impl.TypeCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SwingBuilderNonCodeMemberContributor extends NonCodeMembersContributor {

  private static final Key<MultiMap<String, PsiMethod>> KEY = Key.create("SwingBuilderNonCodeMemberContributor.KEY");

  private static final Object METHOD_KIND = "SwingBuilder_builder_method";

  private static class MyBuilder {
    private final PsiManager myManager;
    private final MultiMap<String, PsiMethod> myResult = new MultiMap<>();
    private final GlobalSearchScope myResolveScope;
    private final PsiElementFactory myFactory;
    private final PsiClass mySwingBuilderClass;
    private final PsiType MANY_OBJECTS;

    private final Map<String, PsiType> myTypeMap = new HashMap<>();

    private MyBuilder(PsiClass swingBuilderClass) {
      myManager = swingBuilderClass.getManager();
      mySwingBuilderClass = swingBuilderClass;
      myResolveScope = swingBuilderClass.getResolveScope();
      myFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
      MANY_OBJECTS = new PsiEllipsisType(type(CommonClassNames.JAVA_LANG_OBJECT));
    }

    public class MyMethodBuilder extends GrLightMethodBuilder {
      private String myNavigationClass;

      public MyMethodBuilder(PsiManager manager, String name) {
        super(manager, name);
        setMethodKind(METHOD_KIND);
        setOriginInfo("SwingBuilder method");
      }

      @NotNull
      @Override
      public PsiElement getNavigationElement() {
        PsiElement res = super.getNavigationElement();
        if (res != this || myNavigationClass == null) return res;

        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(myNavigationClass, getResolveScope());
        if (aClass == null) return res;

        PsiMethod[] methods = aClass.findMethodsByName("newInstance", false);
        if (methods.length != 1) return aClass;

        return methods[0];
      }

      //@Override
      //public MyMethodBuilder addParameter(@NotNull String name, @NotNull String type, boolean isOptional) {
      //  return (MyMethodBuilder)addParameter(name, type(type), isOptional);
      //}

      //public MyMethodBuilder addClosureParam() {
      //  addParameter("closure", GroovyCommonClassNames.GROOVY_LANG_CLOSURE, true);
      //  return this;
      //}

      public MyMethodBuilder setNavigationClass(String navigationClass) {
        myNavigationClass = navigationClass;
        return this;
      }
    }

    @NotNull
    private PsiType type(@NotNull String typeName) {
      PsiType res = myTypeMap.get(typeName);
      if (res == null) {
        res = myFactory.createTypeByFQClassName(typeName, myResolveScope);
        myTypeMap.put(typeName, res);
      }
      return res;
    }

    private void add(@NotNull PsiMethod method) {
      myResult.putValue(method.getName(), method);
    }

    private MyMethodBuilder method(String name, String returnType, @Nullable String navigationClass) {
      MyMethodBuilder res = new MyMethodBuilder(myManager, name);
      res.setModifiers(GrModifierFlags.PUBLIC_MASK);
      res.setReturnType(type(returnType));
      res.setContainingClass(mySwingBuilderClass);
      if (navigationClass != null) {
        assert navigationClass.startsWith("groovy.swing.");
        res.setNavigationClass(navigationClass);
      }
      return res;
    }

    private void methodObject(String name, String returnType, @Nullable String navigationClass) {
      methodObject(name, returnType, navigationClass, null);
    }

    private void methodObject(String name, String returnType, @Nullable String navigationClass,
                              @Nullable Map<String, NamedArgumentDescriptor> namedArg) {
      MyMethodBuilder method = method(name, returnType, navigationClass);
      method.addParameter("map", type(CommonClassNames.JAVA_UTIL_MAP), true);
      method.addParameter("params", MANY_OBJECTS, false);
      if (namedArg != null) {
        method.setNamedParameters(namedArg);
      }
      add(method);
    }

    //private MyMethodBuilder methodWithAttr(String name, String returnType, @Nullable String navigationClass) {
    //  return method(name, returnType, navigationClass).addParameter("attr", CommonClassNames.JAVA_UTIL_MAP, true);
    //}

    //private MyMethodBuilder method(String name, String returnType, @Nullable String navigationClass, String paramName, @Nullable String paramType, boolean isOptional) {
    //  MyMethodBuilder res = methodWithAttr(name, returnType, navigationClass);
    //  res.addParameter(paramName, paramType == null ? returnType : paramType, isOptional);
    //  res.addClosureParam();
    //
    //  return res;
    //}

    //private void beanFactory(@Nullable String factoryName, String name, String returnType, boolean parameterOptional) {
    //  add(method(name, returnType, factoryName, "value", CommonClassNames.JAVA_LANG_STRING, false));
    //  add(method(name, returnType, factoryName,"value", returnType, parameterOptional));
    //}

    //private MyMethodBuilder acceptAllMethod(String name, String returnType, @Nullable String navigationClass) {
    //  return acceptAllMethodLeaf(name, returnType, navigationClass).addClosureParam();
    //}

    //private MyMethodBuilder acceptAllMethodLeaf(String name, String returnType, @Nullable String navigationClass) {
    //  return methodWithAttr(name, returnType, navigationClass).addParameter("value", CommonClassNames.JAVA_LANG_OBJECT, true);
    //}

    private void registerExplicitMethod(String name, String realMethodName) {
      for (PsiMethod method : mySwingBuilderClass.findMethodsByName(realMethodName, false)) {
        add(GrMethodWrapper.wrap(method));
      }
    }

    private void generateMethods() {
      // registerSupportNodes()
      methodObject("action", "javax.swing.Action", "groovy.swing.factory.ActionFactory");

      methodObject("actions", CommonClassNames.JAVA_UTIL_LIST, "groovy.swing.factory.CollectionFactory");

      methodObject("map", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.MapFactory");

      methodObject("imageIcon", "javax.swing.ImageIcon", "groovy.swing.factory.ImageIconFactory",
                   ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
                     .put("image", new TypeCondition(type("java.awt.Image")))
                     .put("url", new TypeCondition(type("java.net.URL")))
                     .put("file", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("resource", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("class", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("description", NamedArgumentDescriptor.TYPE_STRING)
                     .build());

      methodObject("buttonGroup", "javax.swing.BoxLayout", "groovy.swing.factory.ButtonGroupFactory");

      methodObject("noparent", CommonClassNames.JAVA_UTIL_LIST, "groovy.swing.factory.CollectionFactory");

      registerExplicitMethod("keyStrokeAction", "createKeyStrokeAction");
      //registerExplicitMethod("shortcut", "shortcut");

      // registerBinding()
      methodObject("bind", "org.codehaus.groovy.binding.FullBinding", "groovy.swing.factory.BindFactory",
                   ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
                     .put("source", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("target", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("update", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("targetProperty", NamedArgumentDescriptor.TYPE_STRING)
                     .put("mutual", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("sourceEvent", NamedArgumentDescriptor.TYPE_STRING)
                     .put("sourceValue", NamedArgumentDescriptor.TYPE_CLOSURE)
                     .put("sourceProperty", NamedArgumentDescriptor.TYPE_STRING)
                     .put("value", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("group", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .build());

      methodObject("bindProxy", "org.codehaus.groovy.binding.BindingProxy", "groovy.swing.factory.BindProxyFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      methodObject("bindGroup", "org.codehaus.groovy.binding.AggregateBinding", "groovy.swing.factory.BindGroupFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP));


      // registerPassThruNodes()
      methodObject("widget", "java.awt.Component", "groovy.swing.factory.WidgetFactory", ImmutableMap
        .<String, NamedArgumentDescriptor>of("widget", new TypeCondition(type("java.awt.Component"))));

      methodObject("container", "java.awt.Component", "groovy.swing.factory.WidgetFactory", ImmutableMap
        .<String, NamedArgumentDescriptor>of("container", new TypeCondition(type("java.awt.Component"))));

      methodObject("bean", CommonClassNames.JAVA_LANG_OBJECT, "groovy.swing.factory.WidgetFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("bean", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      // registerWindows()
      methodObject("dialog", "javax.swing.JDialog", "groovy.swing.factory.DialogFactory",
                   ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
                     .put("owner", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("defaultButtonProperty", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("show", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .build());

      methodObject("fileChooser", "javax.swing.JFileChooser", null);

      methodObject("frame", "javax.swing.JFrame", "groovy.swing.factory.FrameFactory", ImmutableMap
        .<String, NamedArgumentDescriptor>of("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP, "show", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      methodObject("optionPane", "javax.swing.JOptionPane", null);

      methodObject("window", "javax.swing.JWindow", "groovy.swing.factory.WindowFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                                                                    "show", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                                                                    "owner", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      // registerActionButtonWidgets()
      methodObject("button", "javax.swing.JButton", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("checkBox", "javax.swing.JCheckBox", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("checkBoxMenuItem", "javax.swing.JCheckBoxMenuItem", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("menuItem", "javax.swing.JMenuItem", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("radioButton", "javax.swing.JRadioButton", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("radioButtonMenuItem", "javax.swing.JRadioButtonMenuItem", "groovy.swing.factory.RichActionWidgetFactory");
      methodObject("toggleButton", "javax.swing.JToggleButton", "groovy.swing.factory.RichActionWidgetFactory");

      // registerTextWidgets()
      methodObject("editorPane", "javax.swing.JEditorPane", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("label", "javax.swing.JLabel", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("passwordField", "javax.swing.JPasswordField", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("textArea", "javax.swing.JTextArea", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("textField", "javax.swing.JTextField", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("textPane", "javax.swing.JTextPane", "groovy.swing.factory.TextArgWidgetFactory");
      methodObject("formattedTextField", "javax.swing.JFormattedTextField", "groovy.swing.factory.FormattedTextFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "format", new TypeCondition(type("java.text.Format")),
                     "value", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      // registerMDIWidgets()
      methodObject("desktopPane", "javax.swing.JDesktopPane", null);
      methodObject("internalFrame", "javax.swing.JInternalFrame", "groovy.swing.factory.InternalFrameFactory");

      // registerBasicWidgets()
      methodObject("colorChooser", "javax.swing.JColorChooser", null);
      methodObject("comboBox", "javax.swing.JComboBox", "groovy.swing.factory.ComboBoxFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("items", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      methodObject("list", "javax.swing.JList", "groovy.swing.factory.ListFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("items", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      methodObject("progressBar", "javax.swing.JProgressBar", null);
      methodObject("separator", "javax.swing.JSeparator", "groovy.swing.factory.SeparatorFactory");
      methodObject("scrollBar", "javax.swing.JScrollBar", null);
      methodObject("slider", "javax.swing.JSlider", null);
      methodObject("spinner", "javax.swing.JSpinner", null);
      methodObject("tree", "javax.swing.JTree", null);

      //registerMenuWidgets()
      methodObject("menu", "javax.swing.JMenu", null);
      methodObject("menuBar", "javax.swing.JMenuBar", null);
      methodObject("popupMenu", "javax.swing.JPopupMenu", null);

      // registerContainers()
      methodObject("panel", "javax.swing.JPanel", null);
      methodObject("scrollPane", "javax.swing.JScrollPane", "groovy.swing.factory.ScrollPaneFactory");
      methodObject("splitPane", "javax.swing.JSplitPane", "groovy.swing.factory.SplitPaneFactory");
      methodObject("tabbedPane", "javax.swing.JTabbedPane", "groovy.swing.factory.TabbedPaneFactory");

      methodObject("toolBar", "javax.swing.JToolBar", null);
      methodObject("viewport", "javax.swing.JViewport", null);
      methodObject("layeredPane", "javax.swing.JLayeredPane", null);

      // registerDataModels()
      methodObject("boundedRangeModel", "javax.swing.DefaultBoundedRangeModel", null);
      methodObject("spinnerDateModel", "javax.swing.SpinnerDateModel", null);
      methodObject("spinnerListModel", "javax.swing.SpinnerListModel", null);
      methodObject("spinnerNumberModel", "javax.swing.SpinnerNumberModel", null);

      // registerTableComponents()
      methodObject("table", "javax.swing.JTable", "groovy.swing.factory.TableFactory");
      methodObject("tableColumn", "javax.swing.table.TableColumn", null);
      methodObject("tableModel", "javax.swing.table.TableModel", "groovy.swing.factory.TableModelFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "tableModel", new TypeCondition(type("javax.swing.table.TableModel")),
                     "model", new TypeCondition(type("groovy.model.ValueModel")),
                     "list", NamedArgumentDescriptor.SIMPLE_ON_TOP
                   ));

      methodObject("propertyColumn", "javax.swing.table.TableColumn", "groovy.swing.factory.PropertyColumnFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "propertyName", NamedArgumentDescriptor.TYPE_STRING,
                     "header", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                     "type", new TypeCondition(type(CommonClassNames.JAVA_LANG_CLASS)),
                     "editable", NamedArgumentDescriptor.SIMPLE_ON_TOP
                   ));

      methodObject("closureColumn", "javax.swing.table.TableColumn", "groovy.swing.factory.ClosureColumnFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "header", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                     "read", new TypeCondition(type(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)),
                     "write", new TypeCondition(type(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)),
                     "type", new TypeCondition(type(CommonClassNames.JAVA_LANG_CLASS))
                   ));

      methodObject("columnModel", "javax.swing.table.TableColumnModel", "groovy.swing.factory.ColumnModelFactory");

      methodObject("column", "javax.swing.table.TableColumn", "groovy.swing.factory.ColumnFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("width", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      // registerBasicLayouts()
      methodObject("borderLayout", "java.awt.BorderLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("cardLayout", "java.awt.CardLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("flowLayout", "java.awt.FlowLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("gridLayout", "java.awt.GridLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("overlayLayout", "javax.swing.OverlayLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("springLayout", "javax.swing.SpringLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("gridBagLayout", "java.awt.GridBagLayout", "groovy.swing.factory.LayoutFactory");
      methodObject("gridBagConstraints", "java.awt.GridBagConstraints", "groovy.swing.factory.LayoutFactory");
      methodObject("gbc", "java.awt.GridBagConstraints", "groovy.swing.factory.LayoutFactory");

      // registerBoxLayout()
      methodObject("boxLayout", "javax.swing.BoxLayout", "groovy.swing.factory.BoxLayoutFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of("axis", NamedArgumentDescriptor.SIMPLE_ON_TOP));

      methodObject("box", "javax.swing.Box", "groovy.swing.factory.BoxFactory", ImmutableMap.<String, NamedArgumentDescriptor>of(
        "axis", new TypeCondition(type("java.lang.Number"))));

      methodObject("hbox", "javax.swing.Box", "groovy.swing.factory.HBoxFactory");
      methodObject("hglue", "java.awt.Component", "groovy.swing.factory.HGlueFactory");
      methodObject("hstrut", "java.awt.Component", "groovy.swing.factory.HStrutFactory", ImmutableMap.<String, NamedArgumentDescriptor>of(
        "width", new TypeCondition(type("java.lang.Number"))));

      methodObject("vbox", "javax.swing.Box", "groovy.swing.factory.VBoxFactory");
      methodObject("vglue", "java.awt.Component", "groovy.swing.factory.VGlueFactory");
      methodObject("vstrut", "java.awt.Component", "groovy.swing.factory.VStrutFactory", ImmutableMap.<String, NamedArgumentDescriptor>of(
        "height", new TypeCondition(type("java.lang.Number"))));

      methodObject("glue", "java.awt.Component", "groovy.swing.factory.GlueFactory");
      methodObject("rigidArea", "java.awt.Component", "groovy.swing.factory.RigidAreaFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "size", new TypeCondition(type("java.awt.Dimension")),
                     "height", new TypeCondition(type("java.lang.Number")),
                     "width", new TypeCondition(type("java.lang.Number"))
                   ));

      // registerTableLayout()
      methodObject("tableLayout", "groovy.swing.impl.TableLayout", "groovy.swing.factory.TableLayoutFactory");
      methodObject("tr", "groovy.swing.impl.TableLayoutRow", "groovy.swing.factory.TRFactory");
      methodObject("td", "groovy.swing.impl.TableLayoutCell", "groovy.swing.factory.TDFactory");

      // registerBorders()
      methodObject("lineBorder", "javax.swing.border.LineBorder", "groovy.swing.factory.LineBorderFactory",
                   ImmutableMap.<String, NamedArgumentDescriptor>of(
                     "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                     "color", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                     "thickness", NamedArgumentDescriptor.SIMPLE_ON_TOP,
                     "roundedCorners", NamedArgumentDescriptor.SIMPLE_ON_TOP
                   ));

      NamedArgumentDescriptor namedArgColor = new TypeCondition(type("java.awt.Color"));

      Map<String, NamedArgumentDescriptor> m = ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
        .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
        .put("highlight", namedArgColor)
        .put("shadow", namedArgColor)
        .put("highlightOuter", namedArgColor)
        .put("highlightInner", namedArgColor)
        .put("shadowOuter", namedArgColor)
        .put("shadowInner", namedArgColor)
        .build();

      methodObject("loweredBevelBorder", "javax.swing.border.Border", "groovy.swing.factory.BevelBorderFactory", m);
      methodObject("raisedBevelBorder", "javax.swing.border.Border", "groovy.swing.factory.BevelBorderFactory", m);

      m = ImmutableMap.of(
        "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
        "highlight", namedArgColor,
        "shadow", namedArgColor
      );

      methodObject("etchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory", m);
      methodObject("loweredEtchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory", m);
      methodObject("raisedEtchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory", m);

      methodObject("titledBorder", "javax.swing.border.TitledBorder", "groovy.swing.factory.TitledBorderFactory",
                   ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
                     .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("title", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("position", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("justification", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("border", new TypeCondition(type("javax.swing.border.Border")))
                     .put("color", namedArgColor)
                     .put("font", new TypeCondition(type("java.awt.Font")))
                     .build());

      methodObject("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory");
      methodObject("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory");
      methodObject("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory", ImmutableMap.of(
        "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
        "top", NamedArgumentDescriptor.TYPE_INTEGER,
        "left", NamedArgumentDescriptor.TYPE_INTEGER,
        "bottom", NamedArgumentDescriptor.TYPE_INTEGER,
        "right", NamedArgumentDescriptor.TYPE_INTEGER
      ));

      methodObject("compoundBorder", "javax.swing.border.CompoundBorder", "groovy.swing.factory.CompoundBorderFactory", ImmutableMap.of(
        "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
        "inner", new TypeCondition(type("javax.swing.border.Border")),
        "outer", new TypeCondition(type("javax.swing.border.Border"))
      ));

      methodObject("matteBorder", "javax.swing.border.Border", "groovy.swing.factory.MatteBorderFactory",
                   ContainerUtil.<String, NamedArgumentDescriptor>immutableMapBuilder()
                     .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("icon", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("color", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("size", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("top", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("left", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("bottom", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .put("right", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                     .build());

      // registerRenderers()
      methodObject("tableCellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory");
      methodObject("listCellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory");
      methodObject("cellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory");
      methodObject("headerRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory");
      methodObject("onRender", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.RendererUpdateFactory");

      // registerEditors()
      methodObject("cellEditor", "groovy.swing.impl.ClosureCellEditor", "groovy.swing.factory.CellEditorFactory");
      methodObject("editorValue", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.CellEditorGetValueFactory");
      methodObject("prepareEditor", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.CellEditorPrepareFactory");
    }

    //private void generateMethods() {
    //  // registerSupportNodes()
    //  beanFactory("groovy.swing.factory.ActionFactory", "action", "javax.swing.Action", true);
    //
    //  add(method("actions", CommonClassNames.JAVA_UTIL_LIST, "groovy.swing.factory.CollectionFactory").addClosureParam());
    //
    //  add(methodWithAttr("map", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.MapFactory").addClosureParam());
    //
    //  add(acceptAllMethod("imageIcon", "javax.swing.ImageIcon", "groovy.swing.factory.ImageIconFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //                              .put("image", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Image")))
    //                              .put("url", new NamedArgumentDescriptor.TypeCondition(type("java.net.URL")))
    //                              .put("file", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("resource", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("class", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("description", NamedArgumentDescriptor.TYPE_STRING)
    //                              .build()
    //        ));
    //
    //  beanFactory("groovy.swing.factory.ButtonGroupFactory", "buttonGroup", "javax.swing.BoxLayout", true);
    //
    //  add(methodWithAttr("noparent", CommonClassNames.JAVA_UTIL_LIST, "groovy.swing.factory.CollectionFactory").addClosureParam());
    //
    //  registerExplicitMethod("keyStrokeAction", "createKeyStrokeAction");
    //  //registerExplicitMethod("shortcut", "shortcut");
    //
    //  // registerBinding()
    //  add(acceptAllMethod("bind", "org.codehaus.groovy.binding.FullBinding", "groovy.swing.factory.BindFactory")
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //            .put("source", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("target", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("update", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("targetProperty", NamedArgumentDescriptor.TYPE_STRING)
    //            .put("mutual", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("sourceEvent", NamedArgumentDescriptor.TYPE_STRING)
    //            .put("sourceValue", NamedArgumentDescriptor.TYPE_CLOSURE)
    //            .put("sourceProperty", NamedArgumentDescriptor.TYPE_STRING)
    //            .put("value", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("group", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .build()
    //        ));
    //
    //  add(acceptAllMethodLeaf("bindProxy", "org.codehaus.groovy.binding.BindingProxy", "groovy.swing.factory.BindProxyFactory")
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //            .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .build()
    //        ));
    //
    //  add(acceptAllMethod("bindGroup", "org.codehaus.groovy.binding.AggregateBinding", "groovy.swing.factory.BindGroupFactory")
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //            .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .build()
    //        ));
    //
    //  // registerPassThruNodes()
    //  add(methodWithAttr("widget", "java.awt.Component", "groovy.swing.factory.WidgetFactory")
    //        .addParameter("component", "java.awt.Component", true)
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>of("widget", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Component")))
    //        ));
    //  add(methodWithAttr("container", "java.awt.Component", "groovy.swing.factory.WidgetFactory")
    //        .addParameter("component", "java.awt.Component", true)
    //        .addClosureParam()
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>of("container", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Component")))
    //        ));
    //  add(methodWithAttr("bean", CommonClassNames.JAVA_LANG_OBJECT, "groovy.swing.factory.WidgetFactory")
    //        .addParameter("bean", CommonClassNames.JAVA_LANG_OBJECT, true)
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //            .put("bean", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .build()
    //        ));
    //
    //  // registerWindows()
    //  add(methodWithAttr("dialog", "javax.swing.JDialog", "groovy.swing.factory.DialogFactory").addParameter("dialog", "javax.swing.JDialog", true).addClosureParam()
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //            .put("owner", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("defaultButtonProperty", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .put("show", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //            .build()
    //        ));
    //
    //  beanFactory(null, "fileChooser", "javax.swing.JFileChooser", true);
    //
    //  add(method("frame", "javax.swing.JFrame", "groovy.swing.factory.FrameFactory", "value", "javax.swing.JFrame", true)
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>of("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP, "show", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //        )
    //  );
    //
    //  beanFactory(null, "optionPane", "javax.swing.JOptionPane", true);
    //
    //  add(method("window", "javax.swing.JWindow", "groovy.swing.factory.WindowFactory", "window", "javax.swing.JWindow", true)
    //        .setNamedParameters(
    //          ImmutableMap.<String, NamedArgumentDescriptor>of("pack", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //                                                           "show", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //                                                           "owner", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //        )
    //  );
    //
    //  // registerActionButtonWidgets()
    //  add(acceptAllMethod("button", "javax.swing.JButton", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("checkBox", "javax.swing.JCheckBox", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("checkBoxMenuItem", "javax.swing.JCheckBoxMenuItem", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("menuItem", "javax.swing.JMenuItem", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("radioButton", "javax.swing.JRadioButton", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("radioButtonMenuItem", "javax.swing.JRadioButtonMenuItem", "groovy.swing.factory.RichActionWidgetFactory"));
    //  add(acceptAllMethod("toggleButton", "javax.swing.JToggleButton", "groovy.swing.factory.RichActionWidgetFactory"));
    //
    //  // registerTextWidgets()
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "editorPane", "javax.swing.JEditorPane", true);
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "label", "javax.swing.JLabel", true);
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "passwordField", "javax.swing.JPasswordField", true);
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "textArea", "javax.swing.JTextArea", true);
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "textField", "javax.swing.JTextField", true);
    //  beanFactory("groovy.swing.factory.TextArgWidgetFactory", "textPane", "javax.swing.JTextPane", true);
    //  add(methodWithAttr("formattedTextField", "javax.swing.JFormattedTextField", "groovy.swing.factory.FormattedTextFactory")
    //      .addClosureParam()
    //      .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //        "format", new NamedArgumentDescriptor.TypeCondition(type("java.text.Format")),
    //        "value", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //      )
    //  );
    //
    //  // registerMDIWidgets()
    //  beanFactory(null, "desktopPane", "javax.swing.JDesktopPane", true);
    //  add(method("internalFrame", "javax.swing.JInternalFrame", "groovy.swing.factory.InternalFrameFactory", "value",
    //             "javax.swing.JInternalFrame", true));
    //
    //  // registerBasicWidgets()
    //  beanFactory(null, "colorChooser", "javax.swing.JColorChooser", true);
    //  add(method("comboBox", "javax.swing.JComboBox", "groovy.swing.factory.ComboBoxFactory", "value", "javax.swing.JComboBox", true)
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of("items", NamedArgumentDescriptor.SIMPLE_ON_TOP))
    //  );
    //  add(acceptAllMethod("list", "javax.swing.JList", "groovy.swing.factory.ListFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of("items", NamedArgumentDescriptor.SIMPLE_ON_TOP))
    //  );
    //  beanFactory(null, "progressBar", "javax.swing.JProgressBar", true);
    //  add(methodWithAttr("separator", "javax.swing.JSeparator", "groovy.swing.factory.SeparatorFactory").addClosureParam());
    //  beanFactory(null, "scrollBar", "javax.swing.JScrollBar", true);
    //  beanFactory(null, "slider", "javax.swing.JSlider", true);
    //  beanFactory(null, "spinner", "javax.swing.JSpinner", true);
    //  beanFactory(null, "tree", "javax.swing.JTree", true);
    //
    //  //registerMenuWidgets()
    //  beanFactory(null, "menu", "javax.swing.JMenu", true);
    //  beanFactory(null, "menuBar", "javax.swing.JMenuBar", true);
    //  beanFactory(null, "popupMenu", "javax.swing.JPopupMenu", true);
    //
    //  // registerContainers()
    //  beanFactory(null, "panel", "javax.swing.JPanel", true);
    //  beanFactory("groovy.swing.factory.ScrollPaneFactory", "scrollPane", "javax.swing.JScrollPane", true);
    //  beanFactory("groovy.swing.factory.SplitPaneFactory", "splitPane", "javax.swing.JSplitPane", true);
    //  beanFactory("groovy.swing.factory.TabbedPaneFactory", "tabbedPane", "javax.swing.JTabbedPane", true);
    //
    //  beanFactory(null, "toolBar", "javax.swing.JToolBar", true);
    //  beanFactory(null, "viewport", "javax.swing.JViewport", true);
    //  beanFactory(null, "layeredPane", "javax.swing.JLayeredPane", true);
    //
    //  // registerDataModels()
    //  beanFactory(null, "boundedRangeModel", "javax.swing.DefaultBoundedRangeModel", true);
    //  beanFactory(null, "spinnerDateModel", "javax.swing.SpinnerDateModel", true);
    //  beanFactory(null, "spinnerListModel", "javax.swing.SpinnerListModel", true);
    //  beanFactory(null, "spinnerNumberModel", "javax.swing.SpinnerNumberModel", true);
    //
    //  // registerTableComponents()
    //  beanFactory("groovy.swing.factory.TableFactory", "table", "javax.swing.JTable", true);
    //  beanFactory(null, "tableColumn", "javax.swing.table.TableColumn", true);
    //  add(method("tableModel", "javax.swing.table.TableModel", "groovy.swing.factory.TableModelFactory", "model",
    //             "javax.swing.table.TableModel", true)
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "tableModel", new NamedArgumentDescriptor.TypeCondition(type("javax.swing.table.TableModel")),
    //          "model", new NamedArgumentDescriptor.TypeCondition(type("groovy.model.ValueModel")),
    //          "list", NamedArgumentDescriptor.SIMPLE_ON_TOP
    //        ))
    //  );
    //  add(methodWithAttr("propertyColumn", "javax.swing.table.TableColumn", "groovy.swing.factory.PropertyColumnFactory")
    //        .addClosureParam()
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "propertyName", NamedArgumentDescriptor.TYPE_STRING,
    //          "header", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "type", new NamedArgumentDescriptor.TypeCondition(type(CommonClassNames.JAVA_LANG_CLASS)),
    //          "editable", NamedArgumentDescriptor.SIMPLE_ON_TOP
    //        ))
    //  );
    //  add(methodWithAttr("closureColumn", "javax.swing.table.TableColumn", "groovy.swing.factory.ClosureColumnFactory")
    //        .addClosureParam()
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "header", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "read", new NamedArgumentDescriptor.TypeCondition(type(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)),
    //          "write", new NamedArgumentDescriptor.TypeCondition(type(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)),
    //          "type", new NamedArgumentDescriptor.TypeCondition(type(CommonClassNames.JAVA_LANG_CLASS))
    //        ))
    //  );
    //  add(method("columnModel", "javax.swing.table.TableColumnModel", "groovy.swing.factory.ColumnModelFactory", "model", "javax.swing.table.TableColumnModel", true));
    //
    //  add(acceptAllMethod("column", "javax.swing.table.TableColumn", "groovy.swing.factory.ColumnFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of("width", NamedArgumentDescriptor.SIMPLE_ON_TOP))
    //  );
    //
    //  // registerBasicLayouts()
    //  beanFactory("groovy.swing.factory.LayoutFactory", "borderLayout", "java.awt.BorderLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "cardLayout", "java.awt.CardLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "flowLayout", "java.awt.FlowLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "gridLayout", "java.awt.GridLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "overlayLayout", "javax.swing.OverlayLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "springLayout", "javax.swing.SpringLayout", true);
    //  beanFactory("groovy.swing.factory.LayoutFactory", "gridBagLayout", "java.awt.GridBagLayout", true);
    //  beanFactory(null, "gridBagConstraints", "java.awt.GridBagConstraints", true);
    //  beanFactory(null, "gbc", "java.awt.GridBagConstraints", true);
    //
    //  // registerBoxLayout()
    //  add(methodWithAttr("boxLayout", "javax.swing.BoxLayout", "groovy.swing.factory.BoxLayoutFactory").addClosureParam()
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of("axis", NamedArgumentDescriptor.SIMPLE_ON_TOP))
    //  );
    //  add(method("box", "javax.swing.Box", "groovy.swing.factory.BoxFactory", "box", null, true)
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "axis", new NamedArgumentDescriptor.TypeCondition(type("java.lang.Number"))))
    //  );
    //  add(methodWithAttr("hbox", "javax.swing.Box", "groovy.swing.factory.HBoxFactory").addClosureParam());
    //  add(methodWithAttr("hglue", "java.awt.Component", "groovy.swing.factory.HGlueFactory").addClosureParam());
    //  add(method("hstrut", "java.awt.Component", "groovy.swing.factory.HStrutFactory", "width", "java.lang.Number", true)
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "width", new NamedArgumentDescriptor.TypeCondition(type("java.lang.Number"))))
    //  );
    //  add(methodWithAttr("vbox", "javax.swing.Box", "groovy.swing.factory.VBoxFactory").addClosureParam());
    //  add(methodWithAttr("vglue", "java.awt.Component", "groovy.swing.factory.VGlueFactory").addClosureParam());
    //  add(method("vstrut", "java.awt.Component", "groovy.swing.factory.VStrutFactory", "height", "java.lang.Number", true)
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "height", new NamedArgumentDescriptor.TypeCondition(type("java.lang.Number"))))
    //  );
    //  add(methodWithAttr("glue", "java.awt.Component", "groovy.swing.factory.GlueFactory").addClosureParam());
    //  add(methodWithAttr("rigidArea", "java.awt.Component", "groovy.swing.factory.RigidAreaFactory").addClosureParam()
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "size", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Dimension")),
    //          "height", new NamedArgumentDescriptor.TypeCondition(type("java.lang.Number")),
    //          "width", new NamedArgumentDescriptor.TypeCondition(type("java.lang.Number"))
    //        ))
    //  );
    //
    //  // registerTableLayout()
    //  add(method("tableLayout", "groovy.swing.impl.TableLayout", "groovy.swing.factory.TableLayoutFactory", "layout", null, true));
    //  add(methodWithAttr("tr", "groovy.swing.impl.TableLayoutRow", "groovy.swing.factory.TRFactory").addClosureParam());
    //  add(methodWithAttr("td", "groovy.swing.impl.TableLayoutCell", "groovy.swing.factory.TDFactory").addClosureParam());
    //
    //  // registerBorders()
    //  add(acceptAllMethod("lineBorder", "javax.swing.border.LineBorder", "groovy.swing.factory.LineBorderFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>of(
    //          "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "color", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "thickness", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "roundedCorners", NamedArgumentDescriptor.SIMPLE_ON_TOP
    //        )).setMethodKind(null)
    //  );
    //
    //  NamedArgumentDescriptor namedArgColor = new NamedArgumentDescriptor.TypeCondition(type("java.awt.Color"));
    //
    //  Map<String, NamedArgumentDescriptor> m = ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //                                    .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                                    .put("highlight", namedArgColor)
    //                                    .put("shadow", namedArgColor)
    //                                    .put("highlightOuter", namedArgColor)
    //                                    .put("highlightInner", namedArgColor)
    //                                    .put("shadowOuter", namedArgColor)
    //                                    .put("shadowInner", namedArgColor)
    //                                  .build();
    //
    //  add(acceptAllMethod("loweredBevelBorder", "javax.swing.border.Border", "groovy.swing.factory.BevelBorderFactory")
    //        .setNamedParameters(m).setMethodKind(null));
    //  add(acceptAllMethod("raisedBevelBorder", "javax.swing.border.Border", "groovy.swing.factory.BevelBorderFactory")
    //        .setNamedParameters(m).setMethodKind(null));
    //
    //
    //  m = ImmutableMap.of(
    //    "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //    "highlight", namedArgColor,
    //    "shadow", namedArgColor
    //  );
    //
    //  add(acceptAllMethod("etchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory")
    //        .setNamedParameters(m).setMethodKind(null));
    //  add(acceptAllMethod("loweredEtchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory")
    //        .setNamedParameters(m).setMethodKind(null));
    //  add(acceptAllMethod("raisedEtchedBorder", "javax.swing.border.Border", "groovy.swing.factory.EtchedBorderFactory")
    //        .setNamedParameters(m).setMethodKind(null));
    //
    //  add(acceptAllMethod("titledBorder", "javax.swing.border.TitledBorder", "groovy.swing.factory.TitledBorderFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //                              .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("title", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("position", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("justification", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("border", new NamedArgumentDescriptor.TypeCondition(type("javax.swing.border.Border")))
    //                              .put("color", namedArgColor)
    //                              .put("font", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Font")))
    //                              .build()
    //        ).setMethodKind(null));
    //
    //  add(method("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory", "size",
    //             CommonClassNames.JAVA_LANG_INTEGER, false).setMethodKind(null));
    //  add(method("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory", "sizesList",
    //             CommonClassNames.JAVA_UTIL_LIST, false)
    //        .setMethodKind(null));
    //  add(methodWithAttr("emptyBorder", "javax.swing.border.Border", "groovy.swing.factory.EmptyBorderFactory").addClosureParam()
    //        .setNamedParameters(ImmutableMap.of(
    //          "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "top", NamedArgumentDescriptor.TYPE_INTEGER,
    //          "left", NamedArgumentDescriptor.TYPE_INTEGER,
    //          "bottom", NamedArgumentDescriptor.TYPE_INTEGER,
    //          "right", NamedArgumentDescriptor.TYPE_INTEGER
    //        ))
    //  );
    //  add(method("compoundBorder", "javax.swing.border.CompoundBorder", "groovy.swing.factory.CompoundBorderFactory", "value",CommonClassNames.JAVA_UTIL_LIST, false)
    //        .setNamedParameters(ImmutableMap.of(
    //          "parent", NamedArgumentDescriptor.SIMPLE_ON_TOP,
    //          "inner", new NamedArgumentDescriptor.TypeCondition(type("javax.swing.border.Border")),
    //          "outer", new NamedArgumentDescriptor.TypeCondition(type("javax.swing.border.Border"))
    //        )));
    //
    //  add(acceptAllMethod("matteBorder", "javax.swing.border.Border", "groovy.swing.factory.MatteBorderFactory")
    //        .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>builder()
    //                              .put("parent", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("icon", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("color", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("size", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("top", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("left", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("bottom", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .put("right", NamedArgumentDescriptor.SIMPLE_ON_TOP)
    //                              .build()
    //        ).setMethodKind(null));
    //
    //  // registerRenderers()
    //  add(methodWithAttr("tableCellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory").addClosureParam());
    //  add(methodWithAttr("listCellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory").addClosureParam());
    //  add(methodWithAttr("cellRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory").addClosureParam());
    //  add(methodWithAttr("headerRenderer", "groovy.swing.impl.ClosureRenderer", "groovy.swing.factory.RendererFactory").addClosureParam());
    //  add(acceptAllMethod("onRender", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.RendererUpdateFactory"));
    //
    //  // registerEditors()
    //  add(methodWithAttr("cellEditor", "groovy.swing.impl.ClosureCellEditor", "groovy.swing.factory.CellEditorFactory").addClosureParam());
    //  add(acceptAllMethod("editorValue", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.CellEditorGetValueFactory"));
    //  add(acceptAllMethod("prepareEditor", CommonClassNames.JAVA_UTIL_MAP, "groovy.swing.factory.CellEditorPrepareFactory"));
    //}
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return;

    MultiMap<String, PsiMethod> methodMap = aClass.getUserData(KEY);
    if (methodMap == null) {
      MyBuilder builder = new MyBuilder(aClass);
      builder.generateMethods();
      methodMap = ((UserDataHolderEx)aClass).putUserDataIfAbsent(KEY, builder.myResult);
    }

    String nameHint = ResolveUtil.getNameHint(processor);

    Collection<? extends PsiMethod> methods = nameHint == null ? methodMap.values() : methodMap.get(nameHint);

    for (PsiMethod method : methods) {
      if (!processor.execute(method, state)) return;
    }
  }

  @Override
  protected String getParentClassName() {
    return "groovy.swing.SwingBuilder";
  }
}
