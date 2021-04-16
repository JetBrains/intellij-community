// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.SmartList;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public abstract class CollectionsListSettings {
  @NonNls
  public static final SortedSet<String> DEFAULT_COLLECTION_LIST;

  static {
    final SortedSet<String> set = new TreeSet<>();
    set.add("java.util.concurrent.ConcurrentHashMap");
    set.add("java.util.concurrent.PriorityBlockingQueue");
    set.add("java.util.ArrayDeque");
    set.add("java.util.ArrayList");
    set.add("java.util.HashMap");
    set.add("java.util.Hashtable");
    set.add("java.util.HashSet");
    set.add("java.util.IdentityHashMap");
    set.add("java.util.LinkedHashMap");
    set.add(CommonClassNames.JAVA_UTIL_LINKED_HASH_SET);
    set.add("java.util.PriorityQueue");
    set.add("java.util.Vector");
    set.add("java.util.WeakHashMap");
    DEFAULT_COLLECTION_LIST = Collections.unmodifiableSortedSet(set);
  }

  private final List<String> myCollectionClassesRequiringCapacity;

  public CollectionsListSettings() {
    myCollectionClassesRequiringCapacity = new SmartList<>(getDefaultSettings());
  }

  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myCollectionClassesRequiringCapacity.clear();
    myCollectionClassesRequiringCapacity.addAll(getDefaultSettings());
    for (Element classElement : node.getChildren("cls")) {
      final String className = classElement.getText();
      if (classElement.getAttributeValue("remove", Boolean.FALSE.toString()).equals(Boolean.TRUE.toString())) {
        myCollectionClassesRequiringCapacity.remove(className);
      }
      else {
        myCollectionClassesRequiringCapacity.add(className);
      }
    }
  }

  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    final Collection<String> defaultToRemoveSettings = new HashSet<>(getDefaultSettings());
    defaultToRemoveSettings.removeAll(myCollectionClassesRequiringCapacity);

    final Set<String> toAdd = new HashSet<>(myCollectionClassesRequiringCapacity);
    toAdd.removeAll(getDefaultSettings());

    for (String className : defaultToRemoveSettings) {
      node.addContent(new Element("cls").setText(className).setAttribute("remove", Boolean.TRUE.toString()));
    }
    for (String className : toAdd) {
      node.addContent(new Element("cls").setText(className));
    }
  }

  protected abstract Collection<String> getDefaultSettings();

  public Collection<String> getCollectionClassesRequiringCapacity() {
    return myCollectionClassesRequiringCapacity;
  }

  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(new ListWrappingTableModel(myCollectionClassesRequiringCapacity,
                                                                     QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.options.column.title")));
    final var panel = new InspectionOptionsPanel();
    panel.addGrowing(UiUtils.createAddRemoveTreeClassChooserPanel(
      QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.options.dialog.title"),
      QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.options.label"),
      table,
      true,
      CommonClassNames.JAVA_LANG_OBJECT));
    return panel;
  }
}
