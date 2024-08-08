// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.google.common.base.MoreObjects;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.impl.SquareStripeButton;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.toolWindow.StripeButton;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.dsl.builder.impl.DslComponentPropertyInternal;
import com.intellij.ui.dsl.gridLayout.Constraints;
import com.intellij.ui.dsl.gridLayout.Grid;
import com.intellij.ui.dsl.gridLayout.GridLayout;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.layout.*;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.actionSystem.ex.CustomComponentAction.ACTION_KEY;

public final class ComponentPropertiesCollector {
  private static final Logger LOG = Logger.getInstance(ComponentPropertiesCollector.class);

  private static final List<String> PROPERTIES = Arrays.asList(
    "ui", "getLocation", "getLocationOnScreen",
    "getSize", "isOpaque", "getBorder",
    "getForeground", "getBackground", "getFont",
    "getCellRenderer", "getCellEditor",
    "getMinimumSize", "getMaximumSize", "getPreferredSize",
    "getPreferredScrollableViewportSize",
    "getText", "toString", "isEditable", "getIcon",
    "getVisibleRect", "getLayout",
    "getAlignmentX", "getAlignmentY",
    "getTooltipText", "getToolTipText", "cursor",
    "isShowing", "isEnabled", "isVisible", "isDoubleBuffered",
    "isFocusable", "isFocusCycleRoot", "isFocusOwner",
    "isValid", "isDisplayable", "isLightweight", "getClientProperties", "getMouseListeners", "getFocusListeners"
  );

  private static final List<String> CHECKERS = Arrays.asList(
    "isForegroundSet", "isBackgroundSet", "isFontSet",
    "isMinimumSizeSet", "isMaximumSizeSet", "isPreferredSizeSet"
  );

  private static final List<String> ACCESSIBLE_CONTEXT_PROPERTIES = Arrays.asList(
    "getAccessibleRole", "getAccessibleName", "getAccessibleDescription",
    "getAccessibleAction", "getAccessibleParent", "getAccessibleChildrenCount",
    "getAccessibleIndexInParent", "getAccessibleRelationSet",
    "getAccessibleStateSet", "getAccessibleEditableText",
    "getAccessibleTable", "getAccessibleText",
    "getAccessibleValue", "accessibleChangeSupport"
  );

  public static @NotNull List<PropertyBean> collect(@NotNull Component component) {
    ComponentPropertiesCollector collector = new ComponentPropertiesCollector();
    collector.collectProperties(component);
    return collector.myProperties;
  }

  private final List<PropertyBean> myProperties = new ArrayList<>();

  private ComponentPropertiesCollector() { }

  private void collectProperties(@NotNull Component component) {
    addProperties("", component, PROPERTIES);

    myProperties.add(new PropertyBean("baseline", component.getBaseline(component.getWidth(), component.getHeight())));

    Pair<String, String> addedAt = getAddedAtStacktrace(component);
    myProperties.add(new PropertyBean(addedAt.first, addedAt.second, addedAt.second != null));

    // Add properties related to Accessibility support. This is useful for manually
    // inspecting what kind (if any) of accessibility support components expose.
    boolean isAccessible = component instanceof Accessible;
    myProperties.add(new PropertyBean("accessible", isAccessible));
    AccessibleContext context = component.getAccessibleContext();
    myProperties.add(new PropertyBean("accessibleContext", context));
    if (isAccessible) {
      addProperties("  ", component.getAccessibleContext(), ACCESSIBLE_CONTEXT_PROPERTIES);
    }

    if (component instanceof Container) {
      addLayoutProperties((Container)component);
    }
    if (component instanceof TextPanel.WithIconAndArrows) {
      myProperties.add(new PropertyBean("icon", ((TextPanel.WithIconAndArrows)component).getIcon()));
    }
    if (component.getParent() != null) {
      LayoutManager layout = component.getParent().getLayout();
      if (layout instanceof com.intellij.ui.layout.migLayout.patched.MigLayout) {
        CC cc = ((com.intellij.ui.layout.migLayout.patched.MigLayout)layout).getComponentConstraints().get(component);
        if (cc != null) {
          addMigLayoutComponentConstraints(cc);
        }
      }
      else if (layout instanceof com.intellij.ui.dsl.gridLayout.GridLayout && component instanceof JComponent) {
        addGridLayoutComponentConstraints(
          Objects.requireNonNull(((com.intellij.ui.dsl.gridLayout.GridLayout)layout).getConstraints((JComponent)component)));
      }
    }

    if (component instanceof ComponentWithEmptyText) {
      String emptyText = ((ComponentWithEmptyText)component).getEmptyText().toString();
      if (!emptyText.isEmpty()) {
        myProperties.add(new PropertyBean("EmptyText", emptyText));
      }
    }
    if (component instanceof EditorComponentImpl editorComponent) {
      CharSequence placeholder = editorComponent.getEditor().getPlaceholder();
      if (placeholder != null && !placeholder.isEmpty()) {
        myProperties.add(new PropertyBean("Editor Placeholder", placeholder));
      }
    }
  }

  private void addProperties(@NotNull String prefix, @NotNull Object component, @NotNull List<String> methodNames) {
    Class<?> clazz = component.getClass();
    myProperties.add(new PropertyBean(prefix + "class", clazz.getName() + "@" + System.identityHashCode(component)));

    if (clazz.isAnonymousClass()) {
      Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        myProperties.add(new PropertyBean(prefix + "superclass", superClass.getName(), true));
      }
    }

    Class<?> declaringClass = clazz.getDeclaringClass();
    if (declaringClass != null) {
      myProperties.add(new PropertyBean(prefix + "declaringClass", declaringClass.getName()));
    }

    if (component instanceof Tree) {
      TreeModel model = ((Tree)component).getModel();
      if (model != null) {
        myProperties.add(new PropertyBean(prefix + "treeModelClass", model.getClass().getName(), true));
      }
    }

    addActionInfo(component);
    addToolbarInfo(component);
    addToolWindowInfo(component);
    addGutterInfo(component);

    UiInspectorContextProvider contextProvider = UiInspectorUtil.getProvider(component);
    if (contextProvider != null) {
      try {
        myProperties.addAll(contextProvider.getUiInspectorContext());
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    StringBuilder classHierarchy = new StringBuilder();
    for (Class<?> cl = clazz.getSuperclass(); cl != null; cl = cl.getSuperclass()) {
      if (!classHierarchy.isEmpty()) classHierarchy.append(" ").append(UIUtil.rightArrow()).append(" ");
      classHierarchy.append(cl.getName());
      if (JComponent.class.getName().equals(cl.getName())) break;
    }
    myProperties.add(new PropertyBean(prefix + "hierarchy", classHierarchy.toString()));

    if (component instanceof Component) {
      DialogWrapper dialog = DialogWrapper.findInstance((Component)component);
      if (dialog != null) {
        myProperties.add(new PropertyBean(prefix + "dialogWrapperClass", dialog.getClass().getName(), true));
      }
    }

    addPropertiesFromMethodNames(prefix, component, methodNames);
  }

  private static List<Method> collectAllMethodsRecursively(Class<?> clazz) {
    ArrayList<Method> list = new ArrayList<>();
    for (Class<?> cl = clazz; cl != null; cl = cl.getSuperclass()) {
      list.addAll(Arrays.asList(cl.getDeclaredMethods()));
    }
    return list;
  }

  private void addPropertiesFromMethodNames(@NotNull String prefix,
                                            @NotNull Object component,
                                            @NotNull List<String> methodNames) {
    Class<?> clazz0 = component.getClass();
    Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
    for (String name : methodNames) {
      String propertyName = ObjectUtils.notNull(StringUtil.getPropertyName(name), name);
      Object propertyValue;
      try {
        try {
          //noinspection ConstantConditions
          propertyValue = ReflectionUtil.findMethod(collectAllMethodsRecursively(clazz), name).invoke(component);
        }
        catch (Exception e) {
          propertyValue = ReflectionUtil.findField(clazz, null, name).get(component);
        }
        boolean changed = false;
        try {
          final String checkerMethodName = "is" + StringUtil.capitalize(propertyName) + "Set";
          if (CHECKERS.contains(checkerMethodName)) {
            //noinspection ConstantConditions
            final Object value = ReflectionUtil.findMethod(Arrays.asList(clazz.getMethods()), checkerMethodName).invoke(component);
            if (value instanceof Boolean) {
              changed = ((Boolean)value).booleanValue();
            }
          }
        }
        catch (Exception ignored) {
        }
        myProperties.add(new PropertyBean(prefix + propertyName, propertyValue, changed));
      }
      catch (Exception ignored) {
      }
    }
  }

  private void addGutterInfo(Object component) {
    Point clickPoint = component instanceof EditorGutterComponentEx
                       ? ClientProperty.get((EditorGutterComponentEx)component, UiInspectorAction.CLICK_INFO_POINT)
                       : null;
    if (clickPoint != null) {
      GutterMark renderer = ((EditorGutterComponentEx)component).getGutterRenderer(clickPoint);
      if (renderer != null) {
        myProperties.add(new PropertyBean("gutter renderer", renderer.getClass().getName(), true));
      }
    }
  }

  private void addActionInfo(Object component) {
    AnAction action = null;
    if (component instanceof ActionButton) {
      action = ((ActionButton)component).getAction();
    }
    else if (component instanceof JComponent) {
      if (component instanceof ActionMenuItem) {
        action = ((ActionMenuItem)component).getAnAction();
      }
      else if (component instanceof ActionMenu) {
        action = ((ActionMenu)component).getAnAction();
      }
      else {
        action = getAction(
          ComponentUtil.findParentByCondition((Component)component, c -> getAction(c) != null)
        );
      }
    }

    if (action != null) {
      myProperties.addAll(UiInspectorActionUtil.collectAnActionInfo(action));
    }
  }

  private void addToolbarInfo(Object component) {
    if (component instanceof ActionToolbarImpl toolbar) {
      JComponent targetComponent = toolbar.getTargetComponent();
      myProperties.add(new PropertyBean("Target component", String.valueOf(targetComponent), true));
    }
  }

  private void addToolWindowInfo(Object component) {
    ToolWindowImpl window;
    if (component instanceof StripeButton stripeButton) {
      // old UI
      window = stripeButton.getToolWindow$intellij_platform_ide_impl();
    }
    else if (component instanceof SquareStripeButton stripeButton) {
      // new UI
      window = stripeButton.getToolWindow();
    }
    else {
      return;
    }

    myProperties.add(new PropertyBean("Tool Window ID", window.getId(), true));
    myProperties.add(new PropertyBean("Tool Window Icon", window.getIcon()));

    ToolWindowFactory contentFactory = ReflectionUtil.getField(ToolWindowImpl.class, window, ToolWindowFactory.class, "contentFactory");
    if (contentFactory != null) {
      myProperties.add(new PropertyBean("Tool Window Factory", contentFactory));
    }
    else {
      ToolWindowEP ep = ToolWindowEP.EP_NAME.findFirstSafe(it -> Objects.equals(it.id, window.getId()));
      if (ep != null && ep.factoryClass != null) {
        myProperties.add(new PropertyBean("Tool Window Factory", ep.factoryClass));
      }
    }
  }

  private void addLayoutProperties(@NotNull Container component) {
    LayoutManager layout = component.getLayout();
    if (layout instanceof GridBagLayout bagLayout) {
      GridBagConstraints defaultConstraints =
        ReflectionUtil.getField(GridBagLayout.class, bagLayout, GridBagConstraints.class, "defaultConstraints");

      myProperties.add(new PropertyBean("GridBagLayout constraints",
                                        String.format("defaultConstraints - %s", toString(defaultConstraints))));
      if (bagLayout.columnWidths != null) addSubValue("columnWidths", Arrays.toString(bagLayout.columnWidths));
      if (bagLayout.rowHeights != null) addSubValue("rowHeights", Arrays.toString(bagLayout.rowHeights));
      if (bagLayout.columnWeights != null) addSubValue("columnWeights", Arrays.toString(bagLayout.columnWeights));
      if (bagLayout.rowWeights != null) addSubValue("rowWeights", Arrays.toString(bagLayout.rowWeights));

      for (Component child : component.getComponents()) {
        addSubValue(UiInspectorUtil.getComponentName(child), toString(bagLayout.getConstraints(child)));
      }
    }
    else if (layout instanceof BorderLayout borderLayout) {

      myProperties.add(new PropertyBean("BorderLayout constraints",
                                        String.format("hgap - %s, vgap - %s", borderLayout.getHgap(), borderLayout.getVgap())));

      for (Component child : component.getComponents()) {
        addSubValue(UiInspectorUtil.getComponentName(child), borderLayout.getConstraints(child));
      }
    }
    else if (layout instanceof CardLayout cardLayout) {
      Integer currentCard = ReflectionUtil.getField(CardLayout.class, cardLayout, null, "currentCard");
      //noinspection UseOfObsoleteCollectionType
      Vector<?> vector = ReflectionUtil.getField(CardLayout.class, cardLayout, Vector.class, "vector");
      String cardDescription = "???";
      if (vector != null && currentCard != null) {
        Object card = vector.get(currentCard);
        cardDescription = ReflectionUtil.getField(card.getClass(), card, String.class, "name");
      }

      myProperties.add(new PropertyBean("CardLayout constraints",
                                        String.format("card - %s, hgap - %s, vgap - %s",
                                                      cardDescription, cardLayout.getHgap(), cardLayout.getVgap())));

      if (vector != null) {
        for (Object card : vector) {
          String cardName = ReflectionUtil.getField(card.getClass(), card, String.class, "name");
          Component child = ReflectionUtil.getField(card.getClass(), card, Component.class, "comp");
          if (child != null) {
            addSubValue(UiInspectorUtil.getComponentName(child), cardName);
          }
        }
      }
    }
    else if (layout instanceof MigLayout migLayout) {

      Object constraints = migLayout.getLayoutConstraints();
      if (constraints instanceof LC) {
        addMigLayoutLayoutConstraints((LC)constraints);
      }
      else {
        myProperties.add(new PropertyBean("MigLayout layout constraints", constraints));
      }

      constraints = migLayout.getColumnConstraints();
      if (constraints instanceof AC) {
        addMigLayoutAxisConstraints("MigLayout column constraints", (AC)constraints);
      }
      else {
        myProperties.add(new PropertyBean("MigLayout column constraints", constraints));
      }

      constraints = migLayout.getRowConstraints();
      if (constraints instanceof AC) {
        addMigLayoutAxisConstraints("MigLayout row constraints", (AC)constraints);
      }
      else {
        myProperties.add(new PropertyBean("MigLayout row constraints", constraints));
      }

      for (Component child : component.getComponents()) {
        addSubValue(UiInspectorUtil.getComponentName(child), migLayout.getComponentConstraints(child));
      }
    }
    else if (layout instanceof com.intellij.ui.layout.migLayout.patched.MigLayout migLayout) {

      addMigLayoutLayoutConstraints(migLayout.getLayoutConstraints());
      addMigLayoutAxisConstraints("MigLayout column constraints", migLayout.getColumnConstraints());
      addMigLayoutAxisConstraints("MigLayout row constraints", migLayout.getRowConstraints());
    }
    else if (layout instanceof com.intellij.ui.dsl.gridLayout.GridLayout) {
      Grid grid = ((GridLayout)layout).getRootGrid();
      myProperties.add(new PropertyBean("GridLayout", null));
      addSubValue("resizableColumns", grid.getResizableColumns());
      addSubValue("columnsGaps", grid.getColumnsGaps());
      addSubValue("resizableRows", grid.getResizableRows());
      addSubValue("rowsGaps", grid.getRowsGaps());
    }
  }

  private void addMigLayoutLayoutConstraints(LC lc) {
    myProperties.add(new PropertyBean("MigLayout layout constraints", lcConstraintToString(lc)));
    UnitValue[] insets = lc.getInsets();
    if (insets != null) {
      List<String> insetsText = ContainerUtil.map(insets, (i) -> unitValueToString(i));
      myProperties.add(new PropertyBean("  lc.insets", "[" + StringUtil.join(insetsText, ", ") + "]"));
    }
    UnitValue alignX = lc.getAlignX();
    UnitValue alignY = lc.getAlignY();
    if (alignX != null || alignY != null) {
      myProperties.add(new PropertyBean("  lc.align", "x: " + unitValueToString(alignX) + "; y: " + unitValueToString(alignY)));
    }
    BoundSize width = lc.getWidth();
    BoundSize height = lc.getHeight();
    if (width != BoundSize.NULL_SIZE || height != BoundSize.NULL_SIZE) {
      myProperties.add(new PropertyBean("  lc.size", "width: " + boundSizeToString(width) + "; height: " + boundSizeToString(height)));
    }
    BoundSize gridX = lc.getGridGapX();
    BoundSize gridY = lc.getGridGapY();
    if (gridX != null || gridY != null) {
      myProperties.add(new PropertyBean("  lc.gridGap", "x: " + boundSizeToString(gridX) + "; y: " + boundSizeToString(gridY)));
    }
    boolean fillX = lc.isFillX();
    boolean fillY = lc.isFillY();
    if (fillX || fillY) {
      myProperties.add(new PropertyBean("  lc.fill", "x: " + fillX + "; y: " + fillY));
    }
    BoundSize packWidth = lc.getPackWidth();
    BoundSize packHeight = lc.getPackHeight();
    if (packWidth != BoundSize.NULL_SIZE || packHeight != BoundSize.NULL_SIZE) {
      myProperties.add(new PropertyBean("  lc.pack", "width: " + packWidth + "; height: " + packHeight +
                                                     "; widthAlign: " + lc.getPackWidthAlign() +
                                                     "; heightAlign: " + lc.getPackHeightAlign()));
    }
  }

  private static String lcConstraintToString(LC constraint) {
    return "isFlowX=" + constraint.isFlowX() +
           " leftToRight=" + constraint.getLeftToRight() +
           " noGrid=" + constraint.isNoGrid() +
           " hideMode=" + constraint.getHideMode() +
           " visualPadding=" + constraint.isVisualPadding() +
           " topToBottom=" + constraint.isTopToBottom() +
           " noCache=" + constraint.isNoCache();
  }

  private void addMigLayoutAxisConstraints(String title, AC ac) {
    myProperties.add(new PropertyBean(title, ac));
    DimConstraint[] constraints = ac.getConstaints();
    for (int i = 0; i < constraints.length; i++) {
      addDimConstraintProperties("  [" + i + "]", constraints[i]);
    }
  }

  private void addMigLayoutComponentConstraints(CC cc) {
    myProperties.add(new PropertyBean("MigLayout component constraints", componentConstraintsToString(cc)));
    DimConstraint horizontal = cc.getHorizontal();
    addDimConstraintProperties("  cc.horizontal", horizontal);
    DimConstraint vertical = cc.getVertical();
    addDimConstraintProperties("  cc.vertical", vertical);
  }

  private void addDimConstraintProperties(String name, DimConstraint constraint) {
    myProperties.add(new PropertyBean(name, dimConstraintToString(constraint)));
    BoundSize size = constraint.getSize();
    if (size != null) {
      addSubValue(name + ".size", boundSizeToString(size));
    }
    UnitValue align = constraint.getAlign();
    if (align != null) {
      addSubValue(name + ".align", unitValueToString(align));
    }
    BoundSize gapBefore = constraint.getGapBefore();
    if (gapBefore != null && !gapBefore.isUnset()) {
      addSubValue(name + ".gapBefore", boundSizeToString(gapBefore));
    }
    BoundSize gapAfter = constraint.getGapAfter();
    if (gapAfter != null && !gapAfter.isUnset()) {
      addSubValue(name + ".gapAfter", boundSizeToString(gapAfter));
    }
  }

  private void addGridLayoutComponentConstraints(Constraints constraints) {
    Grid grid = constraints.getGrid();

    myProperties.add(new PropertyBean("GridLayout component constraints", null));
    addSubValue("grid", grid.getClass().getSimpleName() + "@" + System.identityHashCode(grid));
    addSubValue("Cell coordinate", new Point(constraints.getX(), constraints.getY()));
    addSubValue("Cell size", new Dimension(constraints.getWidth(), constraints.getHeight()));
    addSubValue("gaps", constraints.getGaps());
    addSubValue("visualPaddings", constraints.getVisualPaddings());
    addSubValue("horizontalAlign", constraints.getHorizontalAlign().name());
    addSubValue("verticalAlign", constraints.getVerticalAlign().name());
    addSubValue("baselineAlign", constraints.getBaselineAlign());
  }

  private void addSubValue(@NotNull String name, @Nullable Object value) {
    myProperties.add(new PropertyBean("  " + name, value));
  }

  private static String componentConstraintsToString(CC cc) {
    CC newCC = new CC();
    StringBuilder stringBuilder = new StringBuilder();
    if (cc.getSkip() != newCC.getSkip()) {
      stringBuilder.append(" skip=").append(cc.getSkip());
    }
    if (cc.getSpanX() != newCC.getSpanX()) {
      stringBuilder.append(" spanX=").append(cc.getSpanX() == LayoutUtil.INF ? "INF" : cc.getSpanX());
    }
    if (cc.getSpanY() != newCC.getSpanY()) {
      stringBuilder.append(" spanY=").append(cc.getSpanY() == LayoutUtil.INF ? "INF" : cc.getSpanY());
    }
    if (cc.getPushX() != null) {
      stringBuilder.append(" pushX=").append(cc.getPushX());
    }
    if (cc.getPushY() != null) {
      stringBuilder.append(" pushY=").append(cc.getPushY());
    }
    if (cc.getSplit() != newCC.getSplit()) {
      stringBuilder.append(" split=").append(cc.getSplit());
    }
    if (cc.isWrap()) {
      stringBuilder.append(" wrap=");
      if (cc.getWrapGapSize() != null) {
        stringBuilder.append(cc.getWrapGapSize());
      }
      else {
        stringBuilder.append("true");
      }
    }
    if (cc.isNewline()) {
      stringBuilder.append(" newline=");
      if (cc.getNewlineGapSize() != null) {
        stringBuilder.append(cc.getNewlineGapSize());
      }
      else {
        stringBuilder.append("true");
      }
    }
    if (cc.getHideMode() != newCC.getHideMode()) {
      stringBuilder.append(" hidemode=").append(cc.getHideMode());
    }
    return stringBuilder.toString().trim();
  }

  private static String dimConstraintToString(DimConstraint constraint) {
    StringBuilder stringBuilder = new StringBuilder();
    DimConstraint newConstraint = new DimConstraint();
    if (!Comparing.equal(constraint.getGrow(), newConstraint.getGrow())) {
      stringBuilder.append(" grow=").append(constraint.getGrow());
    }
    if (constraint.getGrowPriority() != newConstraint.getGrowPriority()) {
      stringBuilder.append(" growPrio=").append(constraint.getGrowPriority());
    }
    if (!Comparing.equal(constraint.getShrink(), newConstraint.getShrink())) {
      stringBuilder.append(" shrink=").append(constraint.getShrink());
    }
    if (constraint.getShrinkPriority() != newConstraint.getShrinkPriority()) {
      stringBuilder.append(" shrinkPrio=").append(constraint.getShrinkPriority());
    }
    if (constraint.isFill() != newConstraint.isFill()) {
      stringBuilder.append(" fill=").append(constraint.isFill());
    }
    if (constraint.isNoGrid() != newConstraint.isNoGrid()) {
      stringBuilder.append(" noGrid=").append(constraint.isNoGrid());
    }
    if (!Objects.equals(constraint.getSizeGroup(), newConstraint.getSizeGroup())) {
      stringBuilder.append(" sizeGroup=").append(constraint.getSizeGroup());
    }
    if (!Objects.equals(constraint.getEndGroup(), newConstraint.getEndGroup())) {
      stringBuilder.append(" endGroup=").append(constraint.getEndGroup());
    }
    return stringBuilder.toString();
  }

  private static String unitValueToString(@Nullable UnitValue unitValue) {
    if (unitValue == null) return "null";
    if (unitValue.getOperation() == UnitValue.STATIC) {
      StringBuilder result = new StringBuilder();
      result.append(unitValue.getValue());
      if (unitValue.getUnitString() != null) {
        result.append(unitValue.getUnitString());
      }
      else {
        int unit = unitValue.getUnit();
        if (unit >= 0) {
          String unitName = MIG_LAYOUT_UNIT_MAP.get().get(unit);
          if (unitName == null) {
            return unitValue.toString();
          }
          result.append(unitName);
        }
      }
      if (unitValue.isHorizontal()) {
        result.append("H");
      }
      else {
        result.append("V");
      }
      return result.toString();
    }
    return unitValue.toString();
  }

  private static String boundSizeToString(BoundSize boundSize) {
    StringBuilder result = new StringBuilder("BoundSize{ ");
    if (boundSize.getMin() != null) {
      result.append("min=").append(unitValueToString(boundSize.getMin())).append(" ");
    }
    if (boundSize.getPreferred() != null) {
      result.append("pref=").append(unitValueToString(boundSize.getPreferred())).append(" ");
    }
    if (boundSize.getMax() != null) {
      result.append("max=").append(unitValueToString(boundSize.getMax())).append(" ");
    }
    if (boundSize.getGapPush()) {
      result.append("push ");
    }
    result.append("}");
    return result.toString();
  }

  private static @NotNull String toString(@Nullable GridBagConstraints constraints) {
    if (constraints == null) return "null";

    MoreObjects.ToStringHelper h = MoreObjects.toStringHelper("");
    appendFieldValue(h, constraints, "gridx");
    appendFieldValue(h, constraints, "gridy");
    appendFieldValue(h, constraints, "gridwidth");
    appendFieldValue(h, constraints, "gridheight");
    appendFieldValue(h, constraints, "weightx");
    appendFieldValue(h, constraints, "weighty");
    appendFieldValue(h, constraints, "anchor");
    appendFieldValue(h, constraints, "fill");
    appendFieldValue(h, constraints, "insets");
    appendFieldValue(h, constraints, "ipadx");
    appendFieldValue(h, constraints, "ipady");
    return h.toString();
  }

  private static void appendFieldValue(@NotNull MoreObjects.ToStringHelper h,
                                       @NotNull GridBagConstraints constraints,
                                       @NotNull String field) {
    Object value = ReflectionUtil.getField(GridBagConstraints.class, constraints, null, field);
    Object defaultValue = ReflectionUtil.getField(GridBagConstraints.class, new GridBagConstraints(), null, field);
    if (!Comparing.equal(value, defaultValue)) h.add(field, value);
  }

  private static @Nullable AnAction getAction(Component c) {
    return ClientProperty.get(c, ACTION_KEY);
  }

  private static @NotNull Pair<@NotNull String, @Nullable String> getAddedAtStacktrace(@NotNull Component component) {
    Throwable throwable = null;
    String propertyName = "added-at";
    String text;
    int first;
    if (component instanceof JComponent c) {
      throwable = (Throwable)c.getClientProperty(DslComponentPropertyInternal.CREATION_STACKTRACE);
    }

    if (throwable == null) {
      throwable = ClientProperty.get(component, UiInspectorAction.ADDED_AT_STACKTRACE);
      if (throwable == null) {
        return new Pair<>(propertyName, null);
      }

      text = ExceptionUtil.getThrowableText(throwable);
      first = text.indexOf("at com.intellij", text.indexOf("at java."));
    }
    else {
      propertyName = "added-at (UI DSL)";
      text = ExceptionUtil.getThrowableText(throwable);
      first = text.indexOf("at com.intellij");
    }

    int last = text.indexOf("at java.awt.EventQueue");
    if (last == -1) last = text.length();
    return new Pair<>(propertyName, last > first && first > 0 ? text.substring(first, last).trim() : null);
  }

  private static final LazyInitializer.LazyValue<Map<Integer, String>> MIG_LAYOUT_UNIT_MAP =
    new LazyInitializer.LazyValue<>(() -> {
      Map<Integer, String> result = new HashMap<>();
      try {
        Field mapField = UnitValue.class.getDeclaredField("UNIT_MAP");
        mapField.setAccessible(true);
        //noinspection unchecked
        Map<String, Integer> map = (Map<String, Integer>)mapField.get(null);
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
          result.put(entry.getValue(), entry.getKey());
        }
      }
      catch (NoSuchFieldException | IllegalAccessException ignored) {
      }
      return result;
    });
}
