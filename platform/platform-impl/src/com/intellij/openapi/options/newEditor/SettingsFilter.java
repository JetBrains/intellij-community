// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.search.ConfigurableHit;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

public abstract class SettingsFilter extends ElementFilter.Active.Impl<SimpleNode> {
  final OptionsEditorContext myContext = new OptionsEditorContext();
  private final @Nullable Project myProject;

  private final SearchTextField mySearch;
  private final List<? extends ConfigurableGroup> myGroups;

  private Set<Configurable> myFiltered;
  private ConfigurableHit myHits;

  private boolean myUpdateRejected;
  private Configurable myLastSelected;

  private volatile SearchableOptionsRegistrar searchableOptionRegistrar;

  SettingsFilter(@Nullable Project project, @NotNull List<? extends ConfigurableGroup> groups, SearchTextField search) {
    SearchableOptionsRegistrarImpl optionRegistrar =
      (SearchableOptionsRegistrarImpl)ApplicationManager.getApplication().getServiceIfCreated(SearchableOptionsRegistrar.class);
    if (optionRegistrar == null || !optionRegistrar.isInitialized()) {
      // if not yet computed, preload it to ensure that will be no delay on user typing
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        SearchableOptionsRegistrarImpl r = (SearchableOptionsRegistrarImpl)SearchableOptionsRegistrar.getInstance();
        r.initialize();
        // must be set only after initializing (to avoid concurrent modifications)
        searchableOptionRegistrar = r;
        ApplicationManager.getApplication().invokeLater(() -> {
           update(r, DocumentEvent.EventType.CHANGE, false, true);
        }, ModalityState.any(), project == null ? ApplicationManager.getApplication().getDisposed() : project.getDisposed());
      });
    }
    else {
      searchableOptionRegistrar = optionRegistrar;
    }

    myProject = project;
    myGroups = groups;
    mySearch = search;
    mySearch.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent event) {
        SearchableOptionsRegistrar registrar = searchableOptionRegistrar;
        if (registrar != null) {
          update(registrar, event.getType(), true, false);
        }
        // request focus if needed on changing the filter text
        IdeFocusManager manager = IdeFocusManager.findInstanceByComponent(mySearch);
        if (manager.getFocusedDescendantFor(mySearch) == null) {
          manager.requestFocus(mySearch, true);
        }
      }
    });
    mySearch.getTextEditor().addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (!mySearch.getText().isEmpty()) {
          if (!myContext.isHoldingFilter()) {
            setHoldingFilter(true);
          }
          if (!mySearch.getTextEditor().isFocusOwner()) {
            mySearch.selectText();
          }
        }
      }
    });
  }

  abstract Configurable getConfigurable(SimpleNode node);

  abstract SimpleNode findNode(Configurable configurable);

  abstract void updateSpotlight(boolean now);

  @Override
  public boolean shouldBeShowing(SimpleNode node) {
    if (myFiltered != null) {
      Configurable configurable = getConfigurable(node);
      if (configurable != null) {
        if (!myFiltered.contains(configurable)) {
          if (myHits != null) {
            Set<Configurable> configurables = myHits.getNameFullHits();
            while (node != null) {
              if (configurable != null) {
                if (configurables.contains(configurable)) {
                  return true;
                }
              }
              node = node.getParent();
              configurable = getConfigurable(node);
            }
          }
          return false;
        }
      }
    }
    return true;
  }

  String getFilterText() {
    String text = mySearch.getText();
    if (text != null) {
      text = text.trim();
      if (1 < text.length()) {
        return text;
      }
    }
    return "";
  }

  private void setHoldingFilter(boolean holding) {
    myContext.setHoldingFilter(holding);
    updateSpotlight(false);
  }

  boolean contains(@NotNull Configurable configurable) {
    return myHits != null && myHits.getNameHits().contains(configurable);
  }

  void update(@Nullable String text) {
    try {
      myUpdateRejected = true;
      mySearch.setText(text);
    }
    finally {
      myUpdateRejected = false;
    }

    SearchableOptionsRegistrar registrar = searchableOptionRegistrar;
    if (registrar != null) {
      update(registrar, DocumentEvent.EventType.CHANGE, false, true);
    }
  }

  private void update(@NotNull SearchableOptionsRegistrar optionRegistrar, @NotNull DocumentEvent.EventType type, boolean adjustSelection, boolean now) {
    if (myUpdateRejected) {
      return;
    }

    String text = getFilterText();
    if (text.isEmpty()) {
      myContext.setHoldingFilter(false);
      myFiltered = null;
    }
    else {
      myContext.setHoldingFilter(true);
      myHits = optionRegistrar.getConfigurables(myGroups, type, myFiltered, text, myProject);
      myFiltered = myHits.getAll();
    }
    mySearch.getTextEditor().setBackground(myFiltered != null && myFiltered.isEmpty()
                                           ? LightColors.RED
                                           : UIUtil.getTextFieldBackground());


    Configurable current = myContext.getCurrentConfigurable();

    boolean shouldMoveSelection = myHits == null || !myHits.getNameFullHits().contains(current) &&
                                                    !myHits.getContentHits().contains(current);

    if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (myFiltered == null || myFiltered.contains(current))) {
      shouldMoveSelection = false;
    }

    Configurable candidate = adjustSelection ? current : null;
    if (shouldMoveSelection && myHits != null) {
      if (!myHits.getNameHits().isEmpty()) {
        candidate = findConfigurable(myHits.getNameHits(), myHits.getNameFullHits());
      }
      else if (!myHits.getContentHits().isEmpty()) {
        candidate = findConfigurable(myHits.getContentHits(), null);
      }
    }
    updateSpotlight(false);

    if ((myFiltered == null || !myFiltered.isEmpty()) && candidate == null && myLastSelected != null) {
      candidate = myLastSelected;
      myLastSelected = null;
    }
    if (candidate == null && current != null) {
      myLastSelected = current;
    }

    if (myFiltered != null &&
        candidate != null) {
      SettingsCounterUsagesCollector.SEARCH.log(getUnnamedConfigurable(candidate).getClass(),
                                                myFiltered.size(),
                                                text.length());
    }

    SimpleNode node = !adjustSelection ? null : findNode(candidate);
    fireUpdate(node, adjustSelection, now);
  }

  private static @NotNull UnnamedConfigurable getUnnamedConfigurable(@NotNull Configurable candidate) {
    return candidate instanceof ConfigurableWrapper ?
           ((ConfigurableWrapper)candidate).getConfigurable() :
           candidate;
  }

  private static Configurable findConfigurable(Set<? extends Configurable> configurables, Set<? extends Configurable> hits) {
    Configurable candidate = null;
    for (Configurable configurable : configurables) {
      if (hits != null && hits.contains(configurable)) {
        return configurable;
      }
      if (candidate == null && !isEmptyParent(configurable)) {
        candidate = configurable;
      }
    }
    return candidate;
  }

  private static boolean isEmptyParent(Configurable configurable) {
    SearchableConfigurable.Parent parent = ConfigurableWrapper.cast(SearchableConfigurable.Parent.class, configurable);
    return parent != null && !parent.hasOwnContent();
  }

  void reload() {
    myLastSelected = null;
    myFiltered = null;
    myHits = null;
    mySearch.setText("");
    myContext.reload();
  }
}
